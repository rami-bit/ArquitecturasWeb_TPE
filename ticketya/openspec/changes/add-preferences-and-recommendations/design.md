# Design

## Context

El sistema ya tiene el núcleo completo y verificado: catálogo con snapshots inmutables, inventario con reservas, compra en saga, emisión de QR, fila virtual para eventos de alta demanda, reportes del organizador, bitácora centralizada y control de acceso. El Gateway valida el JWT y propaga a los servicios la identidad derivada del `sub` y los roles, sin reenviar el `Bearer`. Sobre esa base, `PDF-F10` es la última funcionalidad pendiente del enunciado y la única que puede cumplir el requisito de al menos una base NoSQL.

Restricciones del código y de los contratos vigentes que condicionan el diseño:

- El Gateway tiene un comodín `Path=/api/**` hacia Catalog; toda ruta nueva debe declararse **antes**. Hoy conviven `/api/queue/**`, `/api/inventory/**`, `/api/orders`, `/api/refunds/**`, `/api/access/**`, `/api/reports/**` y `/api/audit/**` antes del comodín.
- El Gateway descarta `X-User-Id`/`X-User-Role` externos y propaga la identidad derivada; ningún servicio confía en encabezados de identidad del cliente.
- `ConsolidatedOpenApiDocumentContractTest` exige que toda operación declare `operationId` único, el parámetro `X-Correlation-Id`, el header `X-Correlation-Id` en **todas** sus respuestas y `application/problem+json` en todo error; las rutas `/internal/**` deben declarar `security: []` y los paths sólo pueden empezar con `/api/` o `/internal/`. Su candado de capacidades no implementadas hoy sólo prohíbe el fragmento `notification`.
- `events.event_type` es texto libre (`VARCHAR(80)` con `CHECK` de no vacío), **no un enum**: las preferencias no pueden validarse contra una lista cerrada.
- Los participantes se guardan en `event_participants.name` (`VARCHAR(200)`) y la ciudad del evento en `event_snapshots.venue_city` (`VARCHAR(120)`).
- `purchase_orders` registra `customer_id`, `event_id` y `status`; los estados pagados son `CONFIRMED` y `PAID_PENDING_TICKETS` (el criterio que ya usa el reporte del organizador).
- `AUDIT_SERVER_PORT` y `QUEUE_SERVER_PORT` comparten el valor `8086` en `.env`; es una colisión previa que no se corrige en esta épica, y el servicio nuevo no la hereda.
- El reactor usa Java 19, Spring Boot 3.2.5 y Testcontainers 1.21.4, con versiones alineadas entre módulos.

## Goals / Non-Goals

**Goals:**

- Que un cliente autenticado registre y consulte sus preferencias sin poder ver ni escribir las de otro.
- Que reciba recomendaciones explicables, deterministas y acotadas, con el motivo de cada sugerencia.
- Que la señal de co-compra provenga de Purchase y los candidatos de Catalog mediante contratos internos, sin acceso cruzado a bases.
- Que una caída de Purchase degrade la respuesta con una advertencia y una caída de Catalog se informe como `503`.
- Que `PDF-NF-PERSISTENCE` quede cumplido con una base NoSQL real, probada con Testcontainers.
- Que la épica sea un incremento vertical observable por el Gateway, con OpenAPI, Postman, Compose y documentación coherentes.

**Non-Goals:**

- Machine learning, embeddings, ranking colaborativo completo o motor externo.
- Cache, TTL o invalidación de recomendaciones.
- Notificaciones (`PDF-F11`), historial de navegación o tracking de clics.
- Interfaz gráfica.
- Cualquier acceso de Preferences a `catalog_db` o `purchase_db`, o copia de eventos y órdenes a MongoDB.
- Control optimista de escritura concurrente sobre preferencias.

## Decisions

### D1 — Las preferencias y las recomendaciones viven en un microservicio propio

`preferences-service`, con su base `preferences-db`, su `Dockerfile` y su ruta `/api/customers/**` publicada por el Gateway.

*Alternativas descartadas:* módulo dentro de `catalog-service` (el catálogo es lectura pública y su base es PostgreSQL; agregarle escritura por cliente y un motor NoSQL mezcla dos responsabilidades y dos tecnologías en el mismo despliegue) y módulo dentro de `purchase-service` (Purchase es dueño de la saga de compra y de datos financieros; personalizar el catálogo desde ahí acopla la venta a una lectura de marketing). *Costo:* un módulo Maven, un puerto, un contenedor de base y una ruta más. *Beneficio:* ownership claro de un dato personal, y la posibilidad de escalar o apagar la personalización sin tocar la compra.

### D2 — MongoDB 7 cumple `PDF-NF-PERSISTENCE`; PostgreSQL JSONB no

El enunciado exige explícitamente **al menos una base NoSQL** en la arquitectura. Las preferencias son un documento con identidad natural (`customerId`), listas de tamaño variable y forma que puede crecer (por ejemplo, artistas favoritos con normalización), y el patrón de acceso es "leer o reemplazar un documento por clave": encaja con un almacén de documentos.

*Alternativas descartadas:* una tabla PostgreSQL con `JSONB` (resolvería el acceso, pero no cumple el requisito de NoSQL y obliga a justificar por qué se declaró una tecnología y se implementó otra) y un archivo o cache en memoria (no es persistente ni verificable). *Costo:* un motor nuevo en Compose, su imagen, su healthcheck, su cliente Java y su contenedor de pruebas. *Beneficio:* requisito no funcional cumplido con la tecnología real, sin forzar al resto del sistema a migrar. *Mitigación del costo:* un único documento por cliente, sin transacciones multi-documento, sin agregaciones y sin índices adicionales; el esquema se valida en la aplicación, no con `$jsonSchema`.

### D3 — Un documento por cliente, con `_id` igual al `customerId`

Base `ticketya_preferences`, colección `customer_preferences`. `_id` es el UUID del cliente y no se crea ningún índice extra: la clave primaria ya resuelve la única consulta.

```json
{
  "_id": "8f14e45f-ceea-467f-a8a4-0f1b2f0f9c11",
  "city": "Buenos Aires",
  "eventTypes": ["CONCIERTO", "TEATRO"],
  "favoriteArtists": ["Artista A", "Artista B"],
  "updatedAt": "2026-11-20T12:00:00Z",
  "version": 3
}
```

*Alternativa descartada:* una colección por lista (`customer_event_types`, `customer_artists`) con lecturas múltiples y un borrado/inserción no atómico entre colecciones.

### D4 — Upsert atómico de un solo documento, sin transacciones

`PUT` ejecuta un `findOneAndReplace` con `upsert=true` y `ReturnDocument.AFTER` sobre `_id = customerId`, escribiendo `updatedAt = now` y `version = versionPrevia + 1` (o `1` en el alta). Un solo documento basta para que la operación sea atómica: no hay transacción multi-documento ni compensación.

*Consecuencia documentada:* dos `PUT` concurrentes del mismo cliente convergen en un documento válido; gana la última escritura y `version` refleja cuántas escrituras exitosas hubo. No se implementa control optimista porque el cliente no envía `version` y el dato es de baja criticidad; la prueba de integración verifica que no se crean documentos duplicados.

### D5 — Purchase expone un contrato interno agregado de señales

`GET /internal/customers/{customerId}/recommendation-signals` devuelve, para un cliente:

```json
{
  "customerId": "…",
  "purchasedEventIds": ["…"],
  "coPurchases": [{ "eventId": "…", "count": 4 }],
  "generatedAt": "2026-11-20T12:00:00Z"
}
```

- `purchasedEventIds`: eventos con al menos una orden en estado `CONFIRMED` o `PAID_PENDING_TICKETS` del cliente.
- `coPurchases`: para cada evento que el cliente **no** compró, la cantidad de clientes distintos que compraron ese evento y además alguno de los eventos del cliente; ordenadas por `count` descendente y `eventId` ascendente, con tope de 200.
- La consulta es de sólo lectura sobre `purchase_orders`, con dos agregaciones acotadas; no se copian órdenes a MongoDB ni se expone ningún dato de pago, monto o ticket.
- Un cliente sin compras devuelve `200` con listas vacías: "sin historial" es un estado válido, no un `404`.

*Alternativas descartadas:* que Preferences lea `purchase_db` (viola ownership y el `AGENTS.md`), copiar órdenes confirmadas a MongoDB (duplica la verdad y crea datos obsoletos cuando una orden se compensa o se devuelve) y pedir a Purchase los candidatos ya puntuados (pondría la fórmula de marketing dentro del servicio de compras).

### D6 — Catalog expone un contrato interno de candidatos

`GET /internal/events/recommendation-candidates?limit=200` devuelve los eventos `PUBLISHED` con `startsAt` futuro, ordenados por `startsAt` ascendente y `eventId` ascendente, acotados por `limit` (por omisión y máximo 200). Cada candidato informa `eventId`, `name`, `eventType`, `startsAt`, `city`, `participants` y `minPrice`, leídos de `event_snapshots` y `snapshot_participants`.

No informa disponibilidad, cupos ni stock: la recomendación nunca afirma que se pueda comprar.

*Alternativas descartadas:* N+1 contra `GET /api/events/{id}` (multiplica llamadas y expone la superficie pública a un consumidor interno) y reutilizar el snapshot de compra por evento (una llamada por evento, con precios de compra que no son el contrato de descubrimiento).

### D7 — Fórmula de puntaje explicable

```text
score = 3 * (eventType ∈ favoriteEventTypes)
      + 2 * min(3, |artistas del evento ∩ favoriteArtists|)
      + 2 * (city == customer.city)
      + min(5, coPurchaseCount)
      + 1 * (startsAt dentro de los próximos 30 días)
```

- Comparaciones de tipo y artista sin distinguir mayúsculas ni espacios extremos, porque Catalog guarda texto libre.
- Cada componente que suma informa su motivo: `FAVORITE_EVENT_TYPE`, `FAVORITE_ARTIST`, `SAME_CITY`, `CO_PURCHASE`, `UPCOMING_FALLBACK`.
- Puntaje máximo teórico: `3 + 6 + 2 + 5 + 1 = 17`.
- Un candidato con `score = 0` no se devuelve: no hay ninguna razón para recomendarlo.

*Alternativas descartadas:* machine learning (no es explicable ni determinista, y el enunciado habilita una versión simplificada), pesos por clic o vista (no hay tracking y no se va a agregar) y un puntaje normalizado (dificulta la prueba y no aporta al cliente).

### D8 — Orden determinista y límite aplicado al final

Orden: `score` descendente, `startsAt` ascendente, `eventId` ascendente. El `limit` se aplica **después** de ordenar, con `1 ≤ limit ≤ 20` y `10` por omisión; fuera de rango es `400`. Si hay menos candidatos que el límite, se devuelven los que existan.

*Alternativa descartada:* ordenar por fecha y cortar antes de puntuar, que dejaría afuera los eventos más afines.

### D9 — Fallback cuando no hay señales

- Si el cliente no tiene preferencias **ni** historial, el servicio devuelve los próximos eventos publicados y futuros (orden `startsAt` ascendente, `eventId` ascendente), cada uno con `score = 0` y el motivo `UPCOMING_FALLBACK`.
- Si hay señales pero ningún candidato suma puntaje, devuelve `200` con `items` vacío: el cliente ya compró todo lo relevante o no hay eventos afines.
- Si no hay candidatos en el catálogo, devuelve `200` con `items` vacío.

*Alternativa descartada:* devolver lista vacía cuando no hay preferencias, que es la peor experiencia para un cliente nuevo.

### D10 — Sin cache de recomendaciones

Cada consulta lee un documento por clave, un agregado acotado en Purchase y hasta 200 candidatos en Catalog. Con ese volumen, un cache agrega TTL, invalidación ante cambios de preferencias, compras nuevas y publicación de eventos, y el riesgo de recomendar eventos ya comprados o cancelados.

*Alternativas descartadas:* cache en memoria con TTL corto (una compra nueva no se refleja y el cliente ve su propio evento comprado) y una colección de recomendaciones precalculadas en MongoDB (convierte una lectura simple en una proyección que hay que invalidar desde tres servicios). *Consecuencia:* la latencia depende de las dos llamadas internas, mitigada por los timeouts de D11. Si el equipo incorpora cache en `PDF-NF-CACHE`, esta decisión se revisa con métricas, no por anticipación.

### D11 — Resiliencia: timeouts explícitos, sin reintentos, degradación asimétrica

Clientes HTTP con timeout de conexión de 2 s y de lectura de 5 s (`PREFERENCES_HTTP_CONNECT_TIMEOUT_MS`, `PREFERENCES_HTTP_READ_TIMEOUT_MS`). Sin reintentos automáticos: un reintento duplica la espera del cliente y no mejora una caída.

- **Purchase falla o expira:** se responde `200` con recomendaciones calculadas sólo con preferencias y `warnings: ["HISTORY_UNAVAILABLE"]`; los motivos de co-compra simplemente no aparecen.
- **Catalog falla o expira:** se responde `503` con `application/problem+json`, porque sin candidatos no hay nada que recomendar y devolver una lista vacía ocultaría la falla.
- **Ambos fallan:** prevalece `503`, porque Catalog es imprescindible.

*Alternativa descartada:* circuit breaker (Resilience4j no está en la línea base y dos timeouts cubren el caso didáctico; pertenece a `PDF-NF-RESILIENCE`).

### D12 — Sólo `CLIENT`, identidad derivada del `sub`, sin `customerId` en la ruta

El Gateway exige JWT con rol `CLIENT` para `/api/customers/**`; el servicio vuelve a exigir la identidad y el rol. La identidad sale exclusivamente del `sub`; el cuerpo no acepta `customerId` (si viene, `400`) y ninguna ruta lo recibe.

`ADMIN` **no** consulta preferencias ajenas: no hay `customerId` en la ruta y agregar un parámetro abriría un IDOR que la épica no necesita. Un `ADMIN` que quiera ver el catálogo usa las rutas públicas existentes.

*Alternativas descartadas:* `GET /api/customers/{customerId}/preferences` (IDOR y una regla de autorización más) y aceptar `X-Customer-Id` (identidad paralela a la del JWT).

### D13 — Contrato HTTP público

| Operación | Ruta | Respuestas |
|---|---|---|
| Registrar/reemplazar preferencias | `PUT /api/customers/me/preferences` | `200`, `400`, `401`, `403` |
| Consultar preferencias | `GET /api/customers/me/preferences` | `200`, `401`, `403` |
| Recomendar eventos | `GET /api/customers/me/recommendations?limit=10` | `200`, `400`, `401`, `403`, `503` |

Contratos internos, accesibles sólo dentro de la red de servicios: `GET /internal/customers/{customerId}/recommendation-signals` (Purchase, `200`) y `GET /internal/events/recommendation-candidates?limit=200` (Catalog, `200`, `400`).

Los errores usan `application/problem+json` con `type`, `title`, `status`, `detail`, `instance` y `correlationId`, y toda respuesta lleva `X-Correlation-Id`.

### D14 — Reglas de validación de las preferencias

| Campo | Regla |
|---|---|
| `city` | Opcional; si viene, no vacía tras `trim` y de hasta 120 caracteres (igual que `venues.city`) |
| `eventTypes` | Opcional; hasta 10 elementos, cada uno no vacío y de hasta 80 caracteres (igual que `events.event_type`); sin duplicados ignorando mayúsculas |
| `favoriteArtists` | Opcional; hasta 20 elementos, cada uno no vacío y de hasta 200 caracteres (igual que `event_participants.name`); sin duplicados ignorando mayúsculas |
| `updatedAt`, `version` | Sólo respuesta; gestionados por el servidor |

Una violación responde `400` con `application/problem+json` e identifica el campo. Los valores se guardan con `trim`; no se normaliza el `case` para no alterar lo que el cliente escribió, y la comparación en el puntaje es insensible a mayúsculas. No se valida el tipo de evento contra una lista cerrada porque Catalog no define un enum: un tipo desconocido simplemente nunca coincide y no suma puntos.

### D15 — Gateway: ruta propia antes del comodín, sin publicar los internos

Se declara `id: preferences-service` con `Path=/api/customers/**` **antes** del comodín de catálogo, con URL `GATEWAY_PREFERENCES_SERVICE_URL` y autorización `hasRole("CLIENT")`. Las rutas `/internal/**` de Catalog y Purchase no se enrutan y siguen respondiendo como ruta no encontrada.

La capacidad `api-gateway` recibe un requisito **agregado**, no uno modificado, siguiendo el precedente de `add-virtual-queue` y `add-organizer-event-reports`: el requisito del comodín de catálogo ya roza el límite de 500 caracteres del validador y su texto describe el enrutamiento vigente, mientras que una familia de rutas nueva se documenta mejor como requisito propio.

### D16 — Contrato OpenAPI y candado de capacidades

Se agregan las cinco operaciones a `docs/openapi.yaml` con `operationId` únicos (`replaceCustomerPreferences`, `getCustomerPreferences`, `getCustomerRecommendations`, `getRecommendationSignals`, `getRecommendationCandidates`), el parámetro `X-Correlation-Id`, el header en todas las respuestas y `security: []` en las internas. Los esquemas nuevos son `CustomerPreferencesRequest`, `CustomerPreferencesResponse`, `EventRecommendationPage`, `RecommendedEvent`, `RecommendationSignals` y `CoPurchaseSignal`, con `required` explícito y ejemplos.

`ConsolidatedOpenApiDocumentContractTest` incorpora las tres operaciones públicas y las dos internas a su conjunto de Entrega 2, con el mínimo de estados de D13; el candado de capacidades no se toca porque ya sólo prohíbe `notification`.

### D17 — Despliegue

`preferences-db` usa la imagen oficial `mongo:7` con volumen propio `preferences-mongo-data` y healthcheck `mongosh --eval "db.adminCommand('ping')"`. `preferences-service` escucha en `8087` (libre; no hereda la colisión `8086` entre `queue-service` y `audit-service`), depende de su base con `condition: service_healthy`, tiene healthcheck contra `/actuator/health` y **no** publica puertos al host.

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| La consulta de co-compra recorre `purchase_orders` dos veces y puede crecer con el volumen | Se acota a los eventos del cliente, se devuelven como máximo 200 co-compras y se mide con la prueba de integración; no hay proyección asíncrona porque el volumen didáctico no lo exige |
| Sin cache, cada recomendación depende de dos llamadas internas | Timeouts de 2 s/5 s, degradación a preferencias y `503` sólo cuando falta el catálogo; la decisión se revisa si `PDF-NF-CACHE` lo justifica |
| Un `eventType` de preferencia no coincide con el texto libre del catálogo ("Concierto" vs "CONCIERTO") | Comparación sin distinguir mayúsculas ni espacios extremos; se documenta en OpenAPI y se cubre con una prueba |
| MongoDB introduce un motor nuevo en el reactor y en CI | Un solo documento por cliente, sin agregaciones ni transacciones; Testcontainers con `org.testcontainers:mongodb` en la misma versión 1.21.4 del resto |
| El fallback con `score = 0` puede parecer inconsistente con "un puntaje cero no se recomienda" | Se documenta como modo explícito: sin preferencias ni historial se devuelven los próximos eventos con `UPCOMING_FALLBACK`, y en modo normal el cero se excluye |
| Agregar operaciones al contrato consolidado puede romper las aserciones de `operationId` y de headers | Las tareas incluyen ejecutar el test consolidado y declarar `operationId`, `X-Correlation-Id` y headers en cada respuesta antes de cerrar |
| `AUDIT_SERVER_PORT` y `QUEUE_SERVER_PORT` comparten `8086` | Es una colisión previa y fuera de alcance; `preferences-service` usa `8087` y no la propaga |
| El cliente podría intentar escribir preferencias de otro cliente | La identidad sale sólo del `sub`, el cuerpo rechaza `customerId` y no existe ruta con identificador; se prueba con dos clientes |
