# Tasks

## 1. Registro de acceso HTTP

- [x] 1.1 Agregar `RequestLoggingFilter` en Catalog, Inventory, Ticket, Purchase, Payment Mock y Audit, con el correlation id en el patrón de log; probarlo con `RequestLoggingFilterTest` en cada módulo.
- [x] 1.2 Agregar `AccessLogWebFilter` y `AuthenticatedIdentityCaptureFilter` en el Gateway; verificar líneas `200`, `403` (con usuario) y `401` (anónimo) en `GatewayEntrega2RoleAuthorizationTest`.

## 2. Audit Service

- [x] 2.1 Crear `services/audit-service` (Spring Boot, JDBC, PostgreSQL propio) con `POST /internal/audit-events`, `GET /api/audit/events` y `GET /api/audit/traces/{correlationId}`; verificar ingesta idempotente, validación, filtros, paginación, traza ordenada y `403` por rol con Testcontainers (`AuditServiceContractTest`).
- [x] 2.2 Agregar `audit-db` y `audit-service` a Compose y `.env`; verificar que el stack queda 13/13 healthy.

## 3. Productores

- [x] 3.1 Implementar `AuditPublisher` asíncrono con reintentos en Purchase, Catalog y Ticket; probar envío, reintento, no reintento de `400`, servicio caído y modo local (`AuditPublisherTest` en los tres módulos).
- [x] 3.2 Auditar compra, reconciliación, devolución (solicitud, decisión y ejecución), cancelación de evento, cambios de estado en Catalog, emisión/revocación y validación de ingreso; mantener verdes las suites existentes de cada módulo.

## 4. Gateway, contratos y verificación

- [x] 4.1 Enrutar `/api/audit/**` sólo para `ADMIN` con `403` problem+json; actualizar `docs/openapi.yaml` y su test contractual.
- [x] 4.2 Ampliar Postman con las trazas de una compra y de una cancelación y con la búsqueda de ingresos; ejecutar la colección por Gateway y la prueba de concurrencia con Audit Service detenido.
