# Design

## Context

El núcleo del sistema está implementado, verificado y archivado: catálogo con snapshots inmutables, inventario con reservas temporales y exclusión mutua en PostgreSQL, pasarela de pagos simulada, compra en saga con idempotencia y compensación, emisión de QR y control de doble venta. Sobre eso, `E2-01` (`add-jwt-and-role-security`) añadió autenticación y autorización por rol en el Gateway, con identidad derivada de los claims validados y propagada a los servicios.

La arquitectura general ya dibujó la fila virtual como componente separado (`Queue[Virtual Queue]`, `design.md` §13 del cambio `establish-ticketya-architecture`) con estas reglas, que este diseño conserva:

- la cola emite un token de admisión firmado y con TTL, ligado al cliente y al evento;
- el token permite intentar comprar pero **no reserva ni garantiza inventario**: Inventory revalida y puede rechazar por falta de stock;
- la cola nunca es autoridad de disponibilidad;
- la versión didáctica puede usar una tabla ordenada y polling de posición; **Redis no es la línea base** y se evalúa sólo si simplifica claramente la cola o si las pruebas muestran una necesidad.

Restricciones del código existente que condicionan el diseño:

- El Gateway tiene un comodín `Path=/api/**` hacia Catalog; toda ruta nueva debe declararse antes.
- El Gateway no reenvía el `Authorization` del cliente y descarta `X-User-Id`/`X-User-Role`, pero deja pasar otros encabezados: `X-Admission-Token` llega intacto a Purchase.
- El snapshot interno de compra (`GET /internal/events/{eventId}/purchase-snapshot`) filtra `status='PUBLISHED'` y no expone el estado; los dos contratos de snapshot (inventario y compra) deben ser idénticos salvo precios, con un test de contrato que lo verifica.
- `PurchaseService.createOrder(...)` es invocado directamente por tests unitarios con Mockito; su firma es parte del contrato de pruebas.
- `ApiProblemException` no tiene factory para `429`, y el conjunto mínimo de estados de `POST /api/orders` está fijo en un test de contrato.
- No existe ninguna secuencia PostgreSQL (`nextval`/`SERIAL`) en el repositorio; el patrón vigente es *lock advisory + `SELECT … FOR UPDATE` + upsert*.
- `schema.sql` es bootstrap idempotente con `ddl-auto: validate`: agregar columnas a tablas existentes requiere `ALTER TABLE … ADD COLUMN IF NOT EXISTS`.

## Goals / Non-Goals

**Goals:**

- Que un cliente de un evento de alta demanda pueda entrar a una fila, conocer su posición y ser habilitado a comprar en orden y por tandas.
- Que la exigencia de admisión sea verificable de punta a punta: sólo aplica a los eventos marcados como de alta demanda y no altera ningún flujo existente.
- Que el orden y la unicidad de las posiciones se sostengan con varias instancias del servicio de la fila.
- Que la fila sea un incremento vertical observable por el Gateway, con contrato OpenAPI, colección Postman y documentación coherentes.

**Non-Goals:**

- Interfaz gráfica de la fila.
- Base NoSQL, Redis, mensajería, WebSocket o push.
- Reencolado prioritario y apertura programada de la venta.
- Notificaciones de la fila (pertenecen a `E2-03`).
- Prueba de carga de la apertura masiva (pertenece a `E2-08`).
- Un servicio de fila dentro de Catalog o Purchase.

## Decisions

### D1 — La fila es un microservicio propio

`queue-service`, con su base `queue_db` y su Dockerfile, publicado por el Gateway bajo `/api/queue/**`.

*Alternativas descartadas:* módulo dentro de `purchase-service` (mezcla dos responsabilidades en una base y hace que una caída del servicio de compras tumbe también la fila) y módulo dentro de `catalog-service` (el catálogo es de lectura pública y la fila es estado mutable y transaccional). *Costo:* un módulo Maven, un puerto, un contenedor de base y una ruta más. *Beneficio:* ownership propio de posiciones y tokens, y un dominio donde el estado mutable no contamina al catálogo.

### D2 — La marca de alta demanda vive en `events` y se lee en vivo

Columna `events.high_demand BOOLEAN NOT NULL DEFAULT FALSE`, editable sólo mientras el evento está en `DRAFT`, expuesta en los dos contratos internos de snapshot y en las dos respuestas públicas del catálogo.

*Alternativas descartadas:* congelar la marca en `event_snapshots` (agrega una columna, obliga a tocar `EventSnapshot.copyOf` y a mantener simétricos los esquemas de los dos snapshots sin ganancia funcional) y la configuración de fila en `queue_db` (saca el atributo del servicio dueño y abre dos verdades). *Precedente:* el detalle público ya lee `e.organizer_id` en vivo desde `events` en lugar de desde el snapshot, y `organizer_id` tampoco está en `event_snapshots`. Como la edición está bloqueada fuera de `DRAFT`, el valor vivo nunca diverge del publicado.

### D3 — La admisión se exige en Purchase, no en el Gateway

`POST /api/orders` exige `X-Admission-Token` **sólo** cuando el snapshot de compra informa `highDemand`.

*Alternativas descartadas:* un filtro del Gateway (obligaría al Gateway a conocer metadatos de eventos y a mantener un cliente HTTP) y validar en ambos (dos fuentes de verdad para la misma decisión). *Consecuencia:* el `403` lo produce Purchase, que puede distinguir "falta admisión" de "evento no comprable".

### D4 — El token viaja en el encabezado `X-Admission-Token`

Token opaco, firmado, de TTL acotado y de un solo uso; se declara como parámetro de encabezado en OpenAPI.

*Alternativas descartadas:* campo en el cuerpo de la orden (obliga a los clientes a mandar un campo vacío y ensucia un DTO estable) y claim del JWT del IdP (el IdP es externo y no emite tokens de TicketYa).

### D5 — La posición se asigna con lock advisory y contador por evento

`queue_counters(event_id, next_position)` y `queue_entries` con `UNIQUE (event_id, client_id)`; la posición se asigna dentro de una transacción que toma `pg_advisory_xact_lock` **por evento** y actualiza el contador.

*Alternativas descartadas:* `nextval`/`SERIAL` (no existe ninguna secuencia en el repositorio y sería un concepto nuevo) y un lock global (serializaría todas las filas entre sí). *Precedente:* `acquireLimit` en Purchase y `lockEvent` en Inventory.

### D6 — Las tandas se abren por tiempo y se calculan perezosamente

En cada consulta se calcula `admitidos = min(clientes en espera, floor((ahora − apertura) / intervalo) × tamaño)`; los turnos dentro del tramo admitido pasan a `ADMITTED` y reciben su token.

*Alternativas descartadas:* tandas por tamaño acumulado (no es determinista sin un disparador) y un `@Scheduled` que promotes lotes (un componente más que mantener sincronizado). *Configuración:* `QUEUE_BATCH_SIZE` (10) y `QUEUE_BATCH_INTERVAL_SECONDS` (15), con valores de prueba de 2 y 1 segundo.

### D7 — Una entrada activa por cliente y evento; las posiciones no se reutilizan

Entrar de nuevo con un turno activo devuelve el mismo turno y la misma posición. Abandonar o expirar cierra el turno y su posición no vuelve a ofrecerse; volver a entrar asigna una posición nueva al final.

*Alternativas descartadas:* reencolado prioritario (regla que el enunciado no pide) y conservar el lugar de quien expiró (complica la semántica de "posición").

### D8 — El token de admisión tiene TTL de 120 segundos y un solo uso

`QUEUE_ADMISSION_TTL_SECONDS` (120), por debajo del TTL de retención de inventario (600 s) y del TTL por evento (`event_inventories.hold_ttl_seconds`). El token se consume al **intentar** la compra; un reintento con la misma `Idempotency-Key` no consume un segundo token, y reutilizarlo con otra clave se rechaza.

*Consecuencia documentada:* si la compra falla tras consumir el token, el cliente vuelve al final de la fila.

### D9 — La fila valida y consume la admisión en su propia base

Purchase llama a `POST /internal/admissions/validate` con `{eventId, clientId, token, idempotencyKey, correlationId}`; la fila busca el turno por el hash del token, comprueba su vigencia, su evento y su cliente, y **marca el consumo atómicamente**.

El token es **opaco y derivado**: la fila lo calcula como `base64url(HMAC-SHA256(secreto_propio, entryId|eventId|clientId|issuedAt))` y lo recalcula y compara en tiempo constante cuando lo presented el cliente. El secreto vive **sólo** dentro del servicio de la fila, así que no hay secreto compartido y **no se persiste ningún token ni su hash** en la base. El uso único se apoya en el estado `CONSUMED`, y la vigencia en `admission_expires_at`. El cliente recupera su token en cada consulta, de modo que perderlo no obliga a reencolar.

*Alternativas descartadas:* token auto-descriptivo validado localmente por Purchase (no permite garantizar el uso único, porque Purchase no puede marcar el token en la base de la fila) y cola como módulo de Purchase (mezcla el consumo con la saga). *Consecuencia:* un `503` nuevo posible en la compra de eventos de alta demanda, coherente con los demás `503` del saga. La llamada ocurre **sólo** cuando el evento es de alta demanda, así que el camino normal de compra no cambia. Purchase guarda el identificador de admisión devuelto en la orden (`admission_id`) para trazabilidad.

### D10 — `403` para la admisión y `409` para el estado de la fila

`403` para token ausente, vencido, ajeno o de otro evento; `409` para "el evento no está en la fila" y "la fila está cerrada".

*Alternativa descartada:* `429` con `Retry-After`, semánticamente más correcto pero obliga a crear un factory que `ApiProblemException` no tiene y a ampliar el conjunto mínimo de estados fijado en `PurchaseOpenApiDocumentContractTest` (`{201,200,202,402,400,403,404,409,503}`).

### D11 — El cierre de la fila es reactivo, no un aviso proactivo

La fila consulta el snapshot interno de Catalog al entrar (sólo si el cliente no tiene turno activo) y al consultar la posición; un evento que ya no está publicado se responde como inexistente (`404` al entrar) o con la cola cerrada (`409` al consultar), y la fila deja de admitir turnos.

*Alternativas descartadas:* aviso proactivo desde Catalog, junto a `InventoryCancellationClient` (una caída de la fila haría rollback de la cancelación de un evento, porque `EventManagementService.cancel()` es transaccional), y aviso desde Purchase (agrega un `503` posible al flujo de cancelación). *Seguridad:* la fila es UX; la autoridad es el snapshot y Purchase sigue rechazando la compra de un evento que no está en venta.

### D12 — Sólo `CLIENT` y `ADMIN` pueden entrar a la fila

Cada cliente ve únicamente su propio turno; los demás roles reciben `403`. `ADMIN` compra también con admisión, para que la fila sea la única puerta de entrada a un evento de alta demanda.

*Nota:* `PurchaseOrderController.resolveRole` ya acepta `CLIENT` y `ADMIN`, de modo que el rol no introduce un cambio de contrato.

### D13 — El contrato HTTP de la fila

| Operación | Ruta | Respuestas |
|---|---|---|
| Entrar | `POST /api/queue/events/{eventId}/entries` | `201` turno creado, `200` turno existente, `401`, `403`, `404`, `409` |
| Consultar | `GET /api/queue/events/{eventId}/entries/me` | `200`, `401`, `403`, `404` |
| Abandonar | `DELETE /api/queue/events/{eventId}/entries/me` | `204`, `401`, `403`, `404` |
| Validar/consumir (interno) | `POST /internal/admissions/validate` | `200`, `403`, `404` |

Los errores usan `application/problem+json` con `type`, `title`, `status`, `detail`, `instance` y `correlationId`.

### D14 — La numeración de historias de Entrega 2 se corrige antes de este cambio

`US-E2-01` queda reservado para la historia de JWT y las historias de Entrega 2 se corren una posición: F6 → `US-E2-02`, F8 → `US-E2-03`, F10 → `US-E2-04`, F11 → `US-E2-05`, F12 → `US-E2-06`. Sin este corrimiento, la historia de la fila y la épica de JWT compartirían identificador, y `US-E2-02` identificaría dos historias distintas.

### D15 — El candado de capacidades de Entrega 2 se abre una palabra

`ConsolidatedOpenApiDocumentContractTest` mantiene la lista `ENTREGA2_CAPABILITY_FRAGMENTS` que prohíbe esas palabras en `docs/openapi.yaml` para que la Entrega 1 no se contamine. Se saca `"queue"` de la lista, igual que se hizo con JWT, y se conservan las demás.

*Alternativa descartada:* renombrar el servicio y sus rutas para evitar la palabra (costo permanente de nomenclatura para esquivar un test).

### D16 — El modelo de datos

```sql
CREATE TABLE IF NOT EXISTS queue_counters (
    event_id UUID PRIMARY KEY,
    next_position BIGINT NOT NULL DEFAULT 1,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS queue_entries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    client_id UUID NOT NULL,
    position BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    admitted_at TIMESTAMP WITH TIME ZONE,
    admission_expires_at TIMESTAMP WITH TIME ZONE,
    consumed_at TIMESTAMP WITH TIME ZONE,
    idempotency_key VARCHAR(120),
    CONSTRAINT uk_queue_entry_position UNIQUE (event_id, position),
    CONSTRAINT ck_queue_entry_status CHECK (status IN ('WAITING','ADMITTED','CONSUMED','EXPIRED','LEFT'))
);
-- Un cliente sólo puede tener un turno ACTIVO por evento; los turnos cerrados
-- (LEFT, EXPIRED, CONSUMED) se conservan para auditoría y no bloquean la reentrada.
CREATE UNIQUE INDEX IF NOT EXISTS uk_queue_entry_active_client
    ON queue_entries (event_id, client_id)
    WHERE status IN ('WAITING','ADMITTED');
CREATE INDEX IF NOT EXISTS ix_queue_entries_event_status_position ON queue_entries (event_id, status, position);
```

Estados: `WAITING → ADMITTED → CONSUMED | EXPIRED | LEFT`. `LEFT`, `EXPIRED` y `CONSUMED` conservan la fila para auditoría y la reentrada crea un turno nuevo con una posición posterior, porque la unicidad del cliente es parcial sobre los estados activos. `queue_counters.opened_at` marca la apertura de la fila del evento y es el origen del cálculo de tandas.

### D17 — La fila conoce la marca de alta demanda preguntando a Catalog

Al entrar, la fila consulta `GET /internal/events/{eventId}/snapshot` (el mismo patrón que `CatalogSnapshotClient` en Inventory) sólo cuando el cliente no tiene un turno activo, y al consultar la posición vuelve a validar que el evento siga publicado. La marca no se persiste en el turno: se lee en vivo. La validación interna opera sobre el turno ya admitido y no vuelve a consultar Catalog. Un evento no publicado se responde `404`.

*Alternativas descartadas:* que el cliente declare el evento de alta demanda (no es una autoridad) y que Purchase le avise (Purchase no es dueño del atributo). *Consecuencia:* la fila tiene una dependencia de Catalog y responde `503` si no la puede alcanzar, igual que Purchase con sus otros servicios.

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| El filtro de idempotencia de Purchase ocurre antes de la validación del snapshot, así que un replay con la misma clave devuelve `200` sin consumir admisión | Es el comportamiento deseado: el replay no debe quemar un segundo token. Se cubre con un escenario explícito |
| Agregar `highDemand` a los DTO de snapshot puede romper el test que exige simetría entre los dos contratos | El delta exige que ambos contratos informen la misma marca; se actualizan los dos esquemas en `docs/openapi.yaml` de forma simétrica |
| `ALTER TABLE … ADD COLUMN IF NOT EXISTS` sobre volúmenes existentes | El bootstrap es idempotente y el `DEFAULT FALSE` mantiene válidos los `INSERT` crudos de los tests; se verifica contra PostgreSQL real |
| Cambiar la firma de `createOrder` rompe las llamadas directas de `PurchaseBusinessRulesTest` | Se actualizan esas dos llamadas conservando la cobertura; la alternativa (sobrecarga) deja dos caminos de entrada para lo mismo |
| El turno se consume al intentar la compra y la compra puede fallar | Se documenta como simplificación: el cliente vuelve al final de la fila; el stock nunca se retiene por la fila |
| El polling de posición no escala a decenas de miles de clientes | Es la simplificación que el enunciado habilita; la prueba de carga de la apertura masiva pertenece a `E2-08` |
| La fila depende de Catalog y de Purchase la consulta | `503` explícitos y coherentes con el resto del sistema; ningún `503` de la fila habilita una compra |
