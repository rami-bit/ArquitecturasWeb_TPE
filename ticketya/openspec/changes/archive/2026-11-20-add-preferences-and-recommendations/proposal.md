# Proposal

## Why

La funcionalidad 10 del enunciado (`PDF-F10`) pide que cada cliente declare sus preferencias —ciudad, tipos de evento y artistas favoritos— y reciba recomendaciones de eventos afines a esas preferencias y a su historial de compras, incluida la señal "quienes compraron este evento también compraron". Hoy TicketYa no personaliza nada: el catálogo devuelve los mismos eventos a todos los clientes y no existe ningún lugar donde guardar preferencias. Además, `PDF-NF-PERSISTENCE` exige al menos una base NoSQL justificada y las cinco bases del sistema son PostgreSQL, así que la épica también es el vehículo natural para cumplir ese requisito.

La funcionalidad está desbloqueada: el catálogo publica eventos con tipo, ciudad, participantes y fecha; Purchase registra órdenes confirmadas con su `customer_id`; y el JWT ya entrega una identidad confiable derivada del `sub`, que permite personalizar sin recibir un `customerId` en la ruta. La épica es, además, la antesala de `PDF-F11` (avisar al cliente cuando su artista favorito toca en su ciudad).

## What Changes

- **Nuevo microservicio `preferences-service`** con su propia base **MongoDB 7** (`preferences-db`), que persiste un documento de preferencias por cliente y calcula recomendaciones bajo demanda.
- **Nuevo contrato público de preferencias** (`PUT`/`GET /api/customers/me/preferences`), autenticado con JWT y resuelto exclusivamente por el `sub`: ninguna ruta recibe un `customerId`.
- **Nuevo contrato público de recomendaciones** (`GET /api/customers/me/recommendations?limit=10`), con `limit` entre 1 y 20, puntaje explicable, motivos y advertencias.
- **Nuevo contrato interno en Purchase** (`GET /internal/customers/{customerId}/recommendation-signals`) que devuelve, agregados y acotados, los eventos comprados y las co-compras del cliente. Preferences no accede a `purchase_db`.
- **Nuevo contrato interno en Catalog** (`GET /internal/events/recommendation-candidates?limit=200`) que devuelve los eventos publicados y futuros con tipo, fecha, ciudad, participantes y precio mínimo. Preferences no accede a la base del catálogo.
- **Fórmula de recomendación explicable y determinista**, sin machine learning: pesos fijos por tipo favorito, artistas favoritos, ciudad, co-compras y proximidad de fecha, con orden `score` descendente, `startsAt` ascendente y `eventId` ascendente.
- **Degradación controlada**: si Purchase no responde, se recomienda sólo por preferencias con la advertencia `HISTORY_UNAVAILABLE`; si Catalog no responde, se responde `503`; sin preferencias ni historial, se devuelven los próximos eventos con el motivo `UPCOMING_FALLBACK`.
- **Ampliación de contratos y despliegue**: `docs/openapi.yaml`, colección Postman, `docker-compose.yml`, `docs/architecture.md`, `README.md`, `TODO.md`, `NEXT.md` y `docs/verification.md`.

No hay cambios rompientes: ningún endpoint existente modifica su contrato y la compra, la fila virtual y los reportes conservan su comportamiento. Las recomendaciones son informativas y nunca reservan, garantizan ni descuentan inventario.

### Simplificaciones declaradas

- **Sin machine learning ni motor externo**: la fórmula es lineal, con pesos fijos y documentados; el objetivo es que el cliente entienda por qué se le recomienda un evento.
- **Sin cache de recomendaciones**: se calculan en cada consulta y se justifica en `design.md`; un dato viejo nunca debe decidir una venta.
- **Sin historial de navegación ni tracking de clics**: la única señal de comportamiento es la compra confirmada.
- **Co-compra acotada**: Purchase devuelve como máximo 200 co-compras ordenadas por cantidad, suficiente para el límite de candidatos del catálogo.
- **Sin control optimista de escritura**: la actualización de preferencias es un reemplazo idempotente con última escritura ganadora y `version` gestionada por el servidor.

## Capabilities

### New Capabilities

- `customer-preferences`: alta, consulta y aislamiento de las preferencias de ciudad, tipos de evento y artistas favoritos de cada cliente, persistidas en MongoDB con upsert atómico.
- `event-recommendations`: recomendaciones explicables de eventos publicados y futuros, con puntaje, motivos, exclusión de eventos ya comprados, degradación ante fallas y fallback.

### Modified Capabilities

- `api-gateway`: publica `/api/customers/**` hacia Preferences Service exigiendo JWT con rol `CLIENT`, con precedencia sobre el comodín de catálogo, y no enruta los contratos internos.
- `purchase-orders`: expone un contrato interno agregado de señales de historial (eventos comprados y co-compras) sin revelar órdenes individuales ni datos de pago.
- `event-discovery`: el criterio de visibilidad del catálogo también rige el contrato interno de candidatos, que sólo incluye eventos `PUBLISHED` con fecha futura, acotados por proximidad.
- `containerized-deployment`: el Compose central incorpora `preferences-db` con la imagen oficial `mongo:7` y `preferences-service` con healthcheck y sin puertos publicados.

## Impact

- **Servicios nuevos**: `services/preferences-service` (Spring Boot 3.2.5, Java 19, Spring Data MongoDB), con `Dockerfile`, healthcheck, entrada en el `pom.xml` raíz, puerto interno 8087 y base `preferences-db` (volumen `preferences-mongo-data`).
- **Servicios modificados**: `purchase-service` (consulta interna de señales sobre `purchase_orders`, sin cambios de esquema), `catalog-service` (consulta interna de candidatos sobre `event_snapshots`, sin cambios de esquema), `api-gateway` (ruta `/api/customers/**` antes del comodín `/api/**` y autorización por rol).
- **Contratos**: `docs/openapi.yaml` (tres operaciones públicas, dos internas, esquemas de preferencias, recomendaciones y señales), `postman/ticketya.postman_collection.json` (flujo completo de preferencias y recomendaciones).
- **Dependencias**: Preferences consume dos contratos internos síncronos con timeouts de conexión de 2 s y lectura de 5 s. Ningún servicio accede a la base de otro y no hay claves foráneas entre bases.
- **Variables de entorno**: `PREFERENCES_SERVER_PORT`, `PREFERENCES_MONGODB_URI`, `PREFERENCES_MONGODB_DATABASE`, `PREFERENCES_CATALOG_SERVICE_URL`, `PREFERENCES_PURCHASE_SERVICE_URL`, `PREFERENCES_CANDIDATE_LIMIT`, `PREFERENCES_HTTP_CONNECT_TIMEOUT_MS`, `PREFERENCES_HTTP_READ_TIMEOUT_MS`, `GATEWAY_PREFERENCES_SERVICE_URL`.
- **Documentación**: `docs/architecture.md`, `docs/Documentacion-de-diseño.md` (historia de `PDF-F10`), `README.md`, `TODO.md`, `NEXT.md`, `docs/verification.md`.
- **Trazabilidad**: `PDF-F10` (preferencias y recomendaciones), `PDF-NF-PERSISTENCE` (base NoSQL justificada), `PDF-NF-SECURITY` (JWT y aislamiento por cliente), `PDF-NF-CACHE` (decisión "sin cache" justificada), `PDF-NF-DEPLOYMENT` (contenedor propio y Compose único).

**Fuera de alcance**: notificaciones de artista favorito en la ciudad (`PDF-F11`), cache o proyecciones asíncronas de recomendaciones, machine learning, embeddings, historial de navegación, ranking colaborativo completo, interfaz gráfica y cualquier acceso de Preferences a las bases de Catalog o Purchase.
