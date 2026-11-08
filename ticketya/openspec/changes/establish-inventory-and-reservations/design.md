# Design

## Context

Ver `proposal.md` para la motivación. El repositorio ya tiene API Gateway, Catalog Service y `catalog_db`; Catalog publica un `EventSnapshot` inmutable con `SnapshotSector` y `SnapshotSeat`, incluyendo `sourceSectorId` y `sourceSeatId`, y hoy el Gateway enruta todo `/api/**` a Catalog. Purchase, Payment y Ticket todavía no existen.

Esta épica implementa `E1-02` del blueprint `establish-ticketya-architecture` y aplica especialmente §6 (ownership), §8 (REST síncrono), §9 (saga), §10 (doble venta), §11 (idempotencia) y §21 (Definition of Done). Inventory será la autoridad de disponibilidad; PostgreSQL será el mecanismo de exclusión mutua, no un cache ni una lectura previa.

**Nota de reconciliación con D2:** el diagrama de contenedores D2 del blueprint dibuja `Gateway → Catalog/Purchase/Ticket` y no dibuja `Gateway → Inventory`. §8 sí incluye la inicialización `Catalog → Inventory`. Para esta épica se agrega una ruta de lectura pública `Gateway → Inventory` y se mantiene `Purchase → Inventory` para los comandos. Es un refinamiento de routing y de contrato de lectura; no cambia el dueño de los datos ni el protocolo crítico de venta. Si el equipo quiere reflejarlo en D2, se hará en un cambio de blueprint separado antes de archivar esta épica; la implementación no modifica el blueprint archivado.

## Goals / Non-Goals

**Goals:**

- Crear `inventory-service` y `inventory_db` con ownership exclusivo del inventario por evento.
- Materializar el layout desde el snapshot publicado de Catalog sin copiar precios.
- Exponer disponibilidad pública y comandos internos idempotentes para la futura saga de Purchase.
- Garantizar un único éxito por asiento o último cupo con PostgreSQL, incluso con dos instancias.
- Liberar reservas vencidas de forma perezosa y con barrido periódico.
- Cumplir el DoD de la épica: Dockerfile, Compose, OpenAPI, Postman, README, pruebas y correlation ID.

**Non-Goals:**

- Órdenes, Purchase Service, Payment Mock, tickets/QR, devoluciones y cancelación masiva.
- Límite de entradas por cliente: pertenece a Purchase, que conoce al comprador.
- Autenticación JWT, autorización por roles y fila virtual.
- Cache, Redis, broker, Outbox/Inbox, mensajería o framework de saga.
- Precios, estado editorial del evento o edición del snapshot de Catalog.
- Exponer los comandos internos de reserva a clientes externos.
- Optimizaciones para eventos masivos más allá de las pruebas de concurrencia exigidas; la carga queda para `E2-08`.

## Decisions

### 1. Inventory es dueño del inventario y Catalog sigue siendo dueño del snapshot

`inventory_db` sólo contiene datos de inventario: configuración materializada, disponibilidad, reservas y sus ítems. No hay foreign keys hacia `catalog_db`; las referencias a `eventId`, `sectorId` y `seatId` se guardan como valores y se validan por contrato.

**Alternativa considerada:** que Catalog mantenga también stock y reservas. Se descarta porque mezcla dos ciclos de vida, impide escalar la competición por asientos de forma independiente y contradice §5 y §6 del blueprint.

### 2. Inventory “tira” del snapshot publicado en Catalog

Inventory obtiene el layout desde un contrato interno de Catalog (`GET /internal/events/{eventId}/snapshot`) cuando necesita materializar un evento por primera vez. La operación es idempotente por `eventId` y no reescribe un inventario existente.

**Por qué pull y no push:** Catalog no necesita conocer Inventory, la publicación no falla si Inventory está caído y los eventos publicados antes de esta épica se inicializan igualmente. La dirección de los datos sigue siendo Catalog → Inventory, como en §8.

**Alternativas consideradas:**

- Catalog llama a Inventory al publicar: se descarta por acoplamiento temporal y porque obligaría a compensar la publicación si Inventory falla.
- Job batch de inicialización: se descarta como mecanismo principal porque el pull perezoso ya cubre la demanda y evita un proceso operativo adicional. El barrido periódico de esta épica sólo expira reservas, no inicializa inventario.

La primera disponibilidad o reserva de un evento puede pagar el costo de la inicialización. Si Catalog no responde, se devuelve `503` y no se crea inventario parcial; la siguiente solicitud reintenta.

### 3. Ruta pública de disponibilidad y comandos internos separados

El Gateway expone `GET /api/inventory/events/{eventId}/availability` y no expone `/internal/**`. Los comandos de reserva, confirmación, liberación y consulta por `orderId` quedan sólo en la red interna para Purchase.

La ruta de Inventory se declara con precedencia sobre la ruta genérica `/api/**` de Catalog. Esto evita que una disponibilidad termine en Catalog y mantiene las rutas existentes sin cambios.

**Alternativa considerada:** enrutar `/api/events/{eventId}/availability` a Inventory. Se descarta porque mezcla dos servicios bajo el mismo prefijo de recurso y hace más frágil la precedencia; un prefijo `/api/inventory` es explícito y deja claro qué servicio responde.

### 4. Modelo relacional

El esquema se crea con un `schema.sql` idempotente, igual que en Catalog, y Hibernate valida las entidades con `ddl-auto: validate`.

```text
event_inventories 1 --- N inventory_sectors 1 --- N inventory_items
        |                                   |
        |                                   +--- N reservation_items
        +--- N reservations 1 --- N reservation_items
```

Tablas y restricciones principales:

| Tabla | Datos | Restricciones |
|---|---|---|
| `event_inventories` | `id`, `event_id`, `snapshot_id`, `hold_ttl_seconds`, `initialized_at` | `event_id` único; TTL entre 60 y 3600; una configuración por evento |
| `inventory_sectors` | `id`, `event_inventory_id`, `source_sector_id`, `name`, `type`, `capacity`, `sold`, `reserved` | único `(event_inventory_id, source_sector_id)`; `capacity > 0`; `sold >= 0`; `reserved >= 0`; `sold + reserved <= capacity` |
| `inventory_items` | `id`, `event_inventory_id`, `sector_id`, `source_seat_id`, `row_label`, `seat_number`, `status`, `reservation_id`, `expires_at`, `sold_at` | único `(event_inventory_id, source_seat_id)`; único `(sector_id, row_label, seat_number)`; estado en `AVAILABLE`, `HELD`, `SOLD` |
| `reservations` | `id`, `order_id`, `event_inventory_id`, `status`, `request_fingerprint`, `expires_at`, `created_at`, `confirmed_at`, `released_at`, `expired_at` | `order_id` único; estado en `ACTIVE`, `CONFIRMED`, `RELEASED`, `EXPIRED`; fingerprint SHA-256 |
| `reservation_items` | `id`, `reservation_id`, `sector_id`, `inventory_item_id`, `quantity` | `quantity > 0`; índice único parcial por asiento y por sector general |

Para sectores numerados, `sold` y `reserved` de `inventory_sectors` no se mantienen: la disponibilidad se deriva de los estados de `inventory_items`. Para admisión general no existen `inventory_items`; se usan los contadores `sold` y `reserved` del sector. Esto evita serializar todos los asientos de un sector numerado sobre una misma fila.

**Identificadores de la API:** en toda la API de Inventory, `sectorId` es el `inventory_sectors.id` y `seatId` es el `inventory_items.id`. La disponibilidad pública es la superficie donde el cliente descubre esos identificadores y los reutiliza en las reservas; así Purchase no depende de los identificadores internos de Catalog. Los `sourceSectorId` y `sourceSeatId` se conservan en la base para trazabilidad y reconciliación con el snapshot, pero no se usan como claves de los comandos de reserva.

`reservation_items` guarda una fila por asiento (cantidad 1) y una fila por sector general con la cantidad solicitada. Los índices únicos parciales impiden duplicar el mismo asiento o el mismo sector general dentro de una reserva:

```sql
CREATE UNIQUE INDEX uk_reservation_item_seat
  ON reservation_items (reservation_id, inventory_item_id)
  WHERE inventory_item_id IS NOT NULL;

CREATE UNIQUE INDEX uk_reservation_item_sector
  ON reservation_items (reservation_id, sector_id)
  WHERE inventory_item_id IS NULL;
```

No se guardan precios en ninguna tabla ni en las respuestas de Inventory.

### 5. API pública de disponibilidad

`GET /api/inventory/events/{eventId}/availability` es pública y no exige identidad. Antes de responder, Inventory garantiza que el inventario exista y expira las reservas vencidas del evento.

Respuesta `200`:

```json
{
  "eventId": "e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b",
  "generatedAt": "2026-11-07T18:00:00Z",
  "sectors": [
    {
      "sectorId": "0b5e9b5e-...",
      "name": "Platea",
      "type": "NUMBERED",
      "capacity": 120,
      "sold": 10,
      "reserved": 2,
      "available": 108,
      "seats": [
        { "seatId": "f1d2...", "row": "A", "number": 1, "status": "AVAILABLE" },
        { "seatId": "f1d2...", "row": "A", "number": 2, "status": "HELD" }
      ]
    },
    {
      "sectorId": "9c8d...",
      "name": "Campo",
      "type": "GENERAL_ADMISSION",
      "capacity": 500,
      "sold": 100,
      "reserved": 10,
      "available": 390
    }
  ]
}
```

Reglas:

- `available = capacity - sold - reserved`.
- `sold` cuenta lugares `SOLD`; `reserved` cuenta lugares `HELD` vigentes.
- Los sectores se ordenan por nombre y luego por identificador; los asientos por `row` y `number`.
- No se exponen `orderId`, `reservationId`, comprador, precios ni datos del pago.
- Si el evento no existe o no tiene snapshot publicado, `404`; si Catalog no responde durante la inicialización, `503`.

**Alternativa considerada:** devolver sólo contadores y un endpoint separado por sector para el mapa de asientos. Se descarta para esta épica porque el cliente necesita elegir asiento y el volumen de un evento didáctico es acotado; si aparece un evento masivo, se puede agregar un filtro por sector sin cambiar el contrato básico.

### 6. Contratos internos para Purchase

Todos los comandos son REST síncronos y viven bajo `/internal`, sin pasar por el Gateway. En Entrega 1 se protegen por aislamiento de red; JWT y autorización llegan en `E2-01`.

| Método y ruta | Propósito | Idempotencia |
|---|---|---|
| `POST /internal/reservations` | Crear una reserva temporal | `orderId` + fingerprint del payload |
| `GET /internal/reservations/by-order/{orderId}` | Recuperar el estado de una reserva | Lectura |
| `GET /internal/reservations/{reservationId}` | Recuperar una reserva por identificador | Lectura |
| `POST /internal/reservations/{reservationId}/confirm` | Confirmar la venta | `reservationId` + estado |
| `POST /internal/reservations/{reservationId}/release` | Liberar la reserva | `reservationId` + estado |
| `PUT /internal/events/{eventId}/hold-ttl` | Configurar la retención del evento | `eventId` + valor |

Configurar el TTL requiere que el inventario del evento ya esté materializado; si no existe, se devuelve `404` sin inicializarlo ni consultar Catalog. Así la operación de configuración no introduce una dependencia síncrona nueva.

Solicitud de reserva:

```json
{
  "orderId": "5e3f0a4e-...",
  "eventId": "e88d7b2d-...",
  "ttlSeconds": 300,
  "items": [
    { "sectorId": "0b5e9b5e-...", "seatId": "f1d2..." },
    { "sectorId": "0b5e9b5e-...", "seatId": "a7c3..." },
    { "sectorId": "9c8d...", "quantity": 2 }
  ]
}
```

`ttlSeconds` es opcional; si falta, se usa `hold_ttl_seconds` del evento. La respuesta `201` devuelve `reservationId`, `orderId`, `status: ACTIVE` y `expiresAt`. Si el `orderId` ya existe con el mismo fingerprint y la reserva está `ACTIVE` o `CONFIRMED`, se devuelve `200` con la reserva existente. Si está `EXPIRED` o `RELEASED`, se devuelve `409` con el estado final. Si el fingerprint difiere, `409` por conflicto de idempotencia.

Confirmación y liberación devuelven el estado final idempotente. Una reserva confirmada no puede liberarse (`409`); una reserva expirada o liberada no puede confirmarse (`409`). La liberación de una reserva ya expirada o liberada devuelve su estado final sin cambios.

Errores internos: `400` por payload inválido, `404` por evento/reserva inexistente, `409` por conflicto de disponibilidad, estado o idempotencia, `503` si Catalog no está disponible para inicializar.

### 7. Reserva atómica y exclusión mutua

La reserva se ejecuta en una única transacción:

1. Se normalizan y validan los ítems (sector, asiento, cantidad, pertenencia y duplicados).
2. Se expiran las reservas vencidas del evento.
3. Se asegura el inventario del evento.
4. Se inserta o recupera la reserva por `orderId` con `INSERT ... ON CONFLICT DO NOTHING` y se compara el fingerprint.
5. Se ordenan los asientos por identificador para reducir deadlocks.
6. Se ejecutan actualizaciones condicionales sobre `inventory_items` y `inventory_sectors`.
7. Si alguna cantidad actualizada no coincide con la solicitada, se lanza la excepción y la transacción revierte todo.

Asiento numerado:

```sql
UPDATE inventory_items
SET status = 'HELD', reservation_id = :reservationId, expires_at = :expiresAt
WHERE event_inventory_id = :eventInventoryId
  AND id = :seatId
  AND sector_id = :sectorId
  AND (status = 'AVAILABLE' OR (status = 'HELD' AND expires_at <= CURRENT_TIMESTAMP));
```

Sector general:

```sql
UPDATE inventory_sectors
SET reserved = reserved + :quantity
WHERE id = :sectorId
  AND capacity - sold - reserved >= :quantity;
```

La reserva de varios asientos es una sola transacción. La comprobación de cantidad actualizada es la que decide si la operación tuvo éxito; no se consulta primero y se decide después.

**Alternativa considerada:** `SELECT ... FOR UPDATE` sobre todos los asientos y luego actualizar. Se descarta como mecanismo principal porque la actualización condicional ya obtiene el lock de fila y reevalúa la condición en PostgreSQL; para los sectores generales, el contador único es el punto de serialización natural.

### 8. Confirmación y liberación

Confirmar:

```sql
UPDATE reservations
SET status = 'CONFIRMED', confirmed_at = CURRENT_TIMESTAMP
WHERE id = :reservationId
  AND status = 'ACTIVE'
  AND expires_at > CURRENT_TIMESTAMP;
```

Si la actualización afecta una fila, se confirman los asientos:

```sql
UPDATE inventory_items
SET status = 'SOLD', sold_at = CURRENT_TIMESTAMP, expires_at = NULL
WHERE reservation_id = :reservationId AND status = 'HELD';
```

Y los sectores generales:

```sql
UPDATE inventory_sectors s
SET sold = s.sold + ri.quantity,
    reserved = s.reserved - ri.quantity
FROM reservation_items ri
WHERE ri.reservation_id = :reservationId
  AND ri.inventory_item_id IS NULL
  AND s.id = ri.sector_id
  AND s.reserved >= ri.quantity;
```

Se verifica que las filas actualizadas coincidan con los ítems de la reserva. Si no coinciden, se revierte la transacción completa. La actualización condicional de `reservations` serializa confirmaciones y liberaciones de la misma reserva.

Liberar:

```sql
UPDATE reservations
SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP
WHERE id = :reservationId AND status = 'ACTIVE';
```

Luego se devuelven los asientos a `AVAILABLE` y se descuenta `reserved` de los sectores generales. Si la reserva ya está `RELEASED` o `EXPIRED`, se devuelve su estado final; si está `CONFIRMED`, se rechaza con `409`.

### 9. Expiración perezosa y barrido periódico

La expiración se ejecuta en dos momentos:

- **Perezosa:** antes de calcular disponibilidad y antes de reservar, para que una reserva vencida no bloquee una operación nueva.
- **Periódica:** un job cada 60 segundos procesa reservas `ACTIVE` vencidas aunque no haya tráfico.

Cada expiración usa una transición condicional para que sólo una instancia libere:

```sql
UPDATE reservations
SET status = 'EXPIRED', expired_at = CURRENT_TIMESTAMP
WHERE id = :reservationId
  AND status = 'ACTIVE'
  AND expires_at <= CURRENT_TIMESTAMP;
```

Sólo si esa actualización afecta una fila, la misma transacción libera los asientos `HELD` de esa reserva y descuenta los cupos generales. La expiración nunca toca `SOLD`. El job se implementa en un bean separado para que el proxy transaccional se aplique a cada reserva y no se procese un lote entero en una transacción gigante.

Índices necesarios: `(status, expires_at)` en `reservations`, `(status, expires_at)` en `inventory_items` y `(reservation_id)` en `inventory_items`.

### 10. Idempotencia persistida

| Operación | Clave | Resultado repetido |
|---|---|---|
| Reservar | `orderId` + fingerprint | Misma reserva si sigue activa o confirmada; estado final en `409` si expiró o se liberó |
| Confirmar | `reservationId` + estado | Mismo estado confirmado sin duplicar ventas |
| Liberar | `reservationId` + estado | Mismo estado liberado o expirado sin duplicar liberaciones |
| Configurar TTL | `eventId` | Último valor válido, idempotente por naturaleza |

El fingerprint se calcula sobre una representación canónica de `eventId`, `ttlSeconds` y los ítems ordenados por tipo e identificador. No se usa el `orderId` como sustituto del contenido: dos payloads distintos con el mismo `orderId` deben fallar.

### 11. Errores, correlation ID y logs

Inventory replica el patrón de Catalog:

- `CorrelationIdFilter` conserva un `X-Correlation-Id` no vacío o genera un UUID.
- Los errores usan `application/problem+json` con `status`, `title`, `detail`, `instance` y `correlationId`.
- Los logs incluyen endpoint, `correlationId`, estado y duración; no incluyen payloads completos, credenciales ni datos personales.
- Las llamadas a Catalog propagan el `X-Correlation-Id` para poder seguir la inicialización entre servicios.

Para las respuestas de error de conflicto se puede incluir el estado de la reserva y su identificador en campos adicionales del problema, pero nunca datos de otros compradores.

### 12. Pruebas

- **Unitarias:** validación de ítems, cálculo de TTL, fingerprint, transiciones de estado y construcción de disponibilidad.
- **Integración con PostgreSQL real:** inicialización idempotente, constraints, disponibilidad, reserva, confirmación, liberación, expiración y rollback ante fallo parcial. Se usa Testcontainers, no sólo H2, porque la garantía depende de SQL y locks de PostgreSQL.
- **Concurrencia:** dos instancias de la aplicación apuntando a la misma base de Testcontainers, o dos contextos Spring independientes, compiten por el mismo asiento, por el último cupo general y por la confirmación de la misma reserva. Se verifica exactamente un éxito y un único estado final.
- **Contrato:** se valida que `docs/openapi.yaml` y el OpenAPI generado por springdoc contengan los paths, esquemas y códigos esperados; los comandos internos quedan documentados pero no publicados por el Gateway.
- **Gateway:** prueba de precedencia `/api/inventory/**` vs `/api/**`, propagación de correlation ID, preservación de errores y ausencia de ruta para `/internal/**`.
- **Postman:** la colección pública cubre disponibilidad a través del Gateway. Los comandos internos se ejercitan con pruebas de integración y con un smoke test documentado dentro de la red de Compose, porque no deben quedar publicados al host.

### 13. Despliegue y nombres

- `services/inventory-service` con `Dockerfile` propio, Java 19, Spring Boot 3.2.5, Maven y `schema.sql` idempotente.
- `inventory-db` como servicio Compose con PostgreSQL 16.4-alpine, base `ticketya_inventory` y volumen `inventory-postgres-data`.
- `inventory-service` en el puerto interno 8082, sin puerto publicado, con `DB_URL`, `DB_USER`, `DB_PASSWORD`, `CATALOG_SERVICE_URL` y `INVENTORY_HOLD_TTL_SECONDS=600`.
- El Gateway recibe `INVENTORY_SERVICE_URL=http://inventory-service:8082`.
- Para no romper el volumen existente, el servicio `postgres` actual sigue siendo el `catalog_db` lógico; se documenta el alias y el nuevo servicio usa el nombre `inventory-db`. El blueprint ya permite documentar el alias en lugar de renombrar infraestructura existente.
- Healthchecks con `wget` para Inventory y `pg_isready` para su base.

### 14. OpenAPI y documentación

Se mantiene `docs/openapi.yaml` como contrato versionado y revisable, y springdoc sigue generando el documento de runtime. La verificación compara que los paths y esquemas principales coincidan. El contrato incluye:

- disponibilidad pública;
- comandos internos de reserva, confirmación, liberación y consulta;
- configuración de TTL;
- esquemas de problema y correlation ID.

La colección Postman se amplía con disponibilidad. Los comandos internos se documentan para Purchase y se prueban dentro de la red, no desde el host.

## Risks / Trade-offs

- **[La primera disponibilidad depende de Catalog]** → La inicialización es idempotente y devuelve `503` claro si Catalog no responde; una solicitud posterior reintenta. Se puede precalentar en el futuro sin cambiar el contrato.
- **[La ruta `/api/inventory/**` podría quedar atrapada por la ruta genérica de Catalog]** → Se declara con orden explícito anterior y se prueba la precedencia en integración.
- **[El barrido periódico compite entre instancias]** → Transición condicional `ACTIVE → EXPIRED` y procesamiento por reserva en transacciones separadas; sólo una instancia libera.
- **[Muchos asientos en una reserva pueden producir deadlocks]** → Se ordenan los asientos por identificador y se mantiene una única transacción acotada; si el volumen crece, se podrá limitar la cantidad por solicitud.
- **[Los contadores generales serializan la competencia por el último cupo]** → Es el comportamiento deseado para garantizar el cupo; los sectores numerados no pasan por ese contador.
- **[El mapa de asientos puede ser grande]** → El contrato permite agregar filtros por sector o paginación en una épica posterior; el alcance actual prioriza la elección de asiento.
- **[La configuración de TTL por evento no tiene interfaz de administración]** → Se expone un contrato interno y se difiere la UI/ administración a una épica posterior.
- **[Los comandos internos no tienen autenticación en E1]** → Sólo se acceden desde la red de Compose y el Gateway no los publica; JWT y autorización se incorporan en `E2-01`.
- **[Los datos de reservas crecen con el tiempo]** → Se conservan para auditoría y reconciliación; una política de retención/limpieza queda fuera de esta épica.

## Migration Plan

1. Agregar `services/inventory-service` al `pom.xml` raíz y crear su POM, `Dockerfile`, configuración y `schema.sql` idempotente.
2. Agregar el contrato interno de snapshot en Catalog sin modificar sus APIs públicas.
3. Incorporar `inventory-db` e `inventory-service` al Compose, con volúmenes y healthchecks.
4. Configurar la ruta de Inventory en el Gateway con precedencia sobre Catalog.
5. Implementar inicialización, disponibilidad, reservas, confirmación, liberación y expiración.
6. Completar OpenAPI, Postman, README y pruebas de concurrencia.
7. Verificar `mvn verify`, builds individuales, `docker compose up --build`, OpenAPI, Postman y smoke test interno.
8. Archivar el cambio sólo después de cumplir el DoD de §21 y la revisión del equipo.

No hay migración de datos existentes: `inventory_db` nace vacía y el inventario se materializa desde los snapshots publicados. El rollback consiste en retirar la ruta y los servicios nuevos del Compose; el endpoint interno de Catalog puede quedar sin uso sin afectar el catálogo.

## Open Questions

- ¿El equipo quiere reflejar `Gateway → Inventory` y `Catalog → Inventory` en D2 del blueprint? Se decide antes de archivar; no cambia el diseño ni los contratos de esta épica.
- ¿Se agregará más adelante un filtro por sector o paginación del mapa de asientos para eventos masivos? Se difiere a la prueba de carga de `E2-08`.
- ¿Se precalentará inventario al publicar en una épica futura? La inicialización perezosa actual no lo requiere.
