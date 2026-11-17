# Tasks

## 1. Ticket Service

- [x] 1.1 Agregar `used_at`, `used_by`, `used_gate` y `access_scans` con `IF NOT EXISTS`; verificar contra PostgreSQL real.
- [x] 1.2 Implementar `TicketQrSigner.verify` con comparación de tiempo constante; probar firmas válidas, ajenas, alteradas y formatos inválidos (`TicketQrVerificationTest`).
- [x] 1.3 Implementar `AccessValidationService` y `AccessControlController` con `ADMITTED`, `ALREADY_USED`, `REVOKED`, `WRONG_EVENT` y `UNKNOWN_TICKET`; probar cada resultado, roles y la consulta de intentos con Testcontainers (`AccessControlContractTest`).
- [x] 1.4 Probar 25 escaneos simultáneos del mismo QR: un `200`, 24 `409` y un único registro `ADMITTED`.

## 2. Gateway y contratos

- [x] 2.1 Enrutar `/api/access/**` a Ticket con autorización por rol en el Gateway; probar roles permitidos y denegados (`GatewayEntrega2RoleAuthorizationTest`).
- [x] 2.2 Documentar las operaciones en `docs/openapi.yaml` y ampliar Postman: ingreso, reingreso, otro evento, revocada, adulterada, rol no autorizado y consulta de intentos.
