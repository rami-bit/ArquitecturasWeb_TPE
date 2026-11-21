# Proposal

## Why

El Gateway ya valida JWT y roles, pero el Compose normal no proporciona un emisor utilizable: las rutas protegidas requieren configurar y operar un IdP aparte. Para la demostración didáctica de TicketYa hace falta un login con cuentas fijas que emita tokens compatibles sin agregar gestión de usuarios.

## What Changes

- Incorporar `auth-service` como proveedor de identidad local con cuentas fijas y UUID distintos para `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF`. Un login con usuario y contraseña devuelve un JWT firmado con el rol asignado por el servidor; no acepta identidad ni rol elegidos por el solicitante.
- Publicar `POST /api/auth/login` sin JWT por el Gateway. Mantener el JWKS accesible sólo en la red interna para que el Gateway valide los tokens emitidos.
- Incluir el servicio y una clave de firma persistente en el Compose normal, de modo que las rutas protegidas funcionen sin un IdP iniciado manualmente. Conservar la posibilidad de configurar otro emisor mediante `JWT_ISSUER_URI` y `JWT_JWK_SET_URI`.
- **Mantener sin reemplazos** `tools/local-test-idp/LocalTestIdp.java` y el arnés que obtiene tokens para identidades arbitrarias. Las pruebas seguirán usando ese emisor mediante configuración aislada del Gateway; no se aceptarán simultáneamente tokens de dos emisores en una misma instancia.
- Documentar cuentas de demostración, arranque, contrato de login, vencimiento y modo de pruebas. No habrá registro, recuperación de contraseña, refresh tokens ni revocación; logout descartará el token en el cliente.
- Registrar la incompatibilidad con `add-desktop-web-interface`: antes de implementar esa interfaz se deberá actualizar su plan para sustituir el selector con JWT hardcodeados por el login local.

## Capabilities

### New Capabilities

- `local-authentication`: autenticar cuentas fijas y emitir JWT firmados compatibles con TicketYa, con identidad y rol asignados por el servidor.

### Modified Capabilities

- `api-gateway`: enrutar el login público a Auth Service antes del comodín de Catalog sin exponer JWKS ni rutas internas.
- `jwt-role-security`: aceptar JWT del emisor confiable configurado, que puede ser el Auth Service local o el IdP de prueba en una corrida aislada.
- `containerized-deployment`: levantar Auth Service con clave persistente y emisor local por defecto, sin publicar su puerto al host.
- `integration-test-harness`: conservar el IdP de prueba y la configuración de emisor aislada para identidades arbitrarias en las verificaciones existentes.

## Impact

Nuevo módulo Spring Boot `services/auth-service`, POM reactor y Dockerfile; Gateway, Compose, OpenAPI consolidado y de Auth, Postman, README y pruebas de autenticación/integración. No cambian las APIs ni las bases de los servicios de dominio. El IdP de prueba y sus scripts permanecen para verificación. Trazabilidad: PDF-NF-SECURITY, PDF-NF-GATEWAY, PDF-NF-DEPLOYMENT y PDF-INTERFAZ-GRAFICA.
