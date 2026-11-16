# Proposal

## Why

La Entrega 1 identifica usuarios mediante encabezados que cualquier cliente puede falsificar. La primera épica de Entrega 2 debe satisfacer PDF-NF-SECURITY incorporando autenticación JWT y autorización por roles, sin agregar requisitos de cuentas o login que el PDF no exige.

## What Changes

- Incorporar JWT firmado con `sub`, roles y expiración, emitido por un proveedor de identidad externo.
- **BREAKING:** exigir JWT en operaciones protegidas y dejar de confiar en `X-User-Id` / `X-User-Role` enviados por el cliente; el Gateway validará el token y propagará a los servicios una identidad derivada de claims validados.
- Proteger las operaciones existentes según sus roles y conservar las reglas de ownership en los servicios.
- Mantener públicas la búsqueda y consulta pública de eventos y la disponibilidad de inventario.
- Actualizar los contratos de Gateway y Purchase que hoy dependen de identidad suministrada por headers y documentar seguridad Bearer en OpenAPI.
- No incorporar login, emisión de tokens, gestión de usuarios, almacenamiento de credenciales, refresh tokens ni una base de identidad propia.

## Capabilities

### New Capabilities

- `jwt-role-security`: validar identidad JWT y autorizar el acceso a las operaciones protegidas usando los roles definidos por TicketYa.

### Modified Capabilities

- `api-gateway`: reemplazar el reenvío de identidad externa sin validar por identidad derivada de un JWT válido, conservando la superficie pública y las rutas internas no publicadas.
- `purchase-orders`: identificar al comprador y resolver ownership a partir del `sub` validado, en lugar de aceptar `X-User-Id` como identidad externa.

## Impact

- **Trazabilidad:** PDF-NF-SECURITY; arquitectura vigente §18; épica E2-01 `add-jwt-and-role-security`.
- **Componentes:** API Gateway, Catalog y Purchase; los servicios mantienen las reglas de rol y ownership existentes.
- **Roles:** `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF`. Este cambio no habilita todavía operaciones de control de acceso QR.
- **Contratos:** `docs/openapi.yaml`, documentación de ejecución y colección Postman requerirán autenticación Bearer para operaciones protegidas; endpoints públicos e internos conservarán su semántica.
- **Fuera de alcance:** integrar o desplegar un proveedor concreto de identidad, emitir tokens, crear usuarios, cambiar reglas de negocio de compra/devolución/eventos o implementar PDF-F8.
