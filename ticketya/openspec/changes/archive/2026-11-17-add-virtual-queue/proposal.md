# Proposal

## Why

La funcionalidad 6 del enunciado (`PDF-F6`) exige que, en eventos marcados como de alta demanda, los clientes entren a una fila virtual: el sistema les informa su posición y los habilita a comprar en orden y por tandas. Hoy TicketYa no tiene ninguna fila: la compra es "primero que llegue, primero que se lleva" y el propio enunciado reconoce que un evento masivo somete al sistema a decenas de miles de compras simultáneas. Sin fila, la apertura de venta de un recital convocante se resuelve con una cadena de `409` en el navegador, que es exactamente la experiencia que el enunciado quiere evitar ("nada de filas ni sobresaltos").

Esta es la segunda entrega, que según la tabla de modalidad "completa la funcionalidad y los aspectos no funcionales restantes", y su primera capacidad pendiente es precisamente la fila virtual. La arquitectura general ya la desenhó como componente aparte (`Queue[Virtual Queue]` en el diagrama de módulos, `design.md` §13 del cambio `establish-ticketya-architecture`) y la mapeó a la épica `E2-02 add-virtual-queue`. Se implementa ahora que el JWT y los roles ya están en pie (`E2-01`, cerrada y archivada) y que el resto del núcleo —reserva temporal, pago simulado, emisión de QR y no doble venta— está verificado y estable.

## What Changes

- **Nuevo microservicio `queue-service`** con base propia (`queue_db`), que administra las entradas de la fila, asigna posiciones, abre tandas y emite tokens de admisión de un solo uso.
- **Nueva marca de alta demanda en el evento**: `highDemand`, editable por el organizador mientras el evento está en `DRAFT` y visible públicamente en el catálogo y en los contratos internos de snapshot.
- **Nuevo contrato HTTP de fila** (`/api/queue/**`), publicado únicamente por el API Gateway y autenticado con JWT: entrar a la fila, consultar la posición propia y abandonar el turno.
- **Exigencia de admisión en la compra**: `POST /api/orders` exige un token de admisión válido cuando el evento es de alta demanda, y no lo exige en ningún otro caso.
- **Token de admisión de un solo uso** ligado a evento y cliente, con vigencia corta, que habilita el intento de compra pero **no reserva ni garantiza inventario**.
- **Ampliación de contratos**: `docs/openapi.yaml`, colección Postman, `docs/architecture.md`, `README.md`, `TODO.md`, `NEXT.md` y `docs/verification.md`.

No hay cambios rompientes: `POST /api/orders` conserva su contrato para los eventos que no son de alta demanda, y el resto de las APIs públicas mantiene su comportamiento.

### Simplificaciones declaradas

El enunciado habilita explícitamente una versión simplificada de la fila virtual ("La fila virtual y las recomendaciones pueden implementarse con una versión simplificada, justificando las simplificaciones"). Se declaran y justifican en `design.md`:

- Consulta de posición por **polling**; no hay push ni WebSocket.
- Almacenamiento **relacional**; Redis no es la línea base y no se incorpora.
- La venta "se abre" al **publicar** el evento; no hay apertura programada por reloj.
- Sin reencolado prioritario: quien abandona o expira vuelve al final de la fila.
- El cierre de la fila ante cancelación o suspensión es **reactivo** (se detecta en la siguiente consulta), no un aviso proactivo.
- Sin interfaz gráfica propia: la fila se consume por API desde el Gateway.

## Capabilities

### New Capabilities

- `virtual-queue`: la fila virtual de los eventos de alta demanda: entrada idempotente, posición, apertura de tandas en orden, token de admisión de un solo uso y sus errores.

### Modified Capabilities

- `event-management`: el evento pasa a llevar una marca de alta demanda que el organizador define en el borrador y que los contratos internos de snapshot publican.
- `event-discovery`: el detalle y la búsqueda públicos del catálogo informan la marca de alta demanda del evento.
- `purchase-orders`: la creación de una orden exige un token de admisión válido cuando el evento publicado es de alta demanda.
- `api-gateway`: el Gateway enruta las operaciones de la fila virtual y las exige autenticadas, sin publicar sus comandos internos.

## Impact

- **Servicios nuevos**: `services/queue-service` (Spring Boot, Java 21, JDBC sobre PostgreSQL, `queue_db`), con `Dockerfile`, healthcheck y entrada propia en `docker-compose.yml` y en el `pom.xml` raíz. Puerto interno 8086, base `queue-db`, volumen `queue-postgres-data`.
- **Servicios modificados**: `catalog-service` (columna `high_demand`, marca en el evento y en los snapshots internos y públicos), `purchase-service` (exigencia y consumo del token de admisión antes de reservar y cobrar), `api-gateway` (ruta `/api/queue/**` antes del comodín `/api/**`).
- **Contratos**: `docs/openapi.yaml` (paths, esquemas, header `X-Admission-Token` y el candado de capacidades de Entrega 2 que hoy prohíbe la palabra `queue`), `postman/ticketya.postman_collection.json` (flujo de fila y reejecución de la colección existente).
- **Dependencias**: la fila consulta el snapshot interno de Catalog (mismo patrón que Inventory con `CatalogSnapshotClient`); Purchase consulta a la fila para validar y consumir la admisión. Ningún servicio accede a la base de otro y no hay claves foráneas entre bases.
- **Variables de entorno**: `QUEUE_SERVER_PORT`, `QUEUE_DB_URL`, `QUEUE_BATCH_SIZE`, `QUEUE_BATCH_INTERVAL_SECONDS`, `QUEUE_ADMISSION_TTL_SECONDS`, `GATEWAY_QUEUE_SERVICE_URL`, `QUEUE_CATALOG_SERVICE_URL`, `PURCHASE_QUEUE_SERVICE_URL`.
- **Documentación**: `docs/Documentacion-de-diseño.md` (historia `US-E2-02`), `docs/architecture.md`, `README.md`, `TODO.md`, `NEXT.md`, `docs/verification.md`.
- **Trazabilidad**: `PDF-F6` (funcionalidad 6 del enunciado) y `PDF-NF-CONCURRENCY` (el orden y la unicidad de las posiciones deben sostenerse con varias instancias del servicio de la fila).

**Fuera de alcance**: la interfaz gráfica, la base NoSQL (pertenece a `E2-04`), las notificaciones de la fila (pertenecen a `E2-03`), la prueba de carga de la apertura masiva (pertenece a `E2-08`), la reconexión prioritaria, Redis, mensajería y la apertura programada de la venta.
