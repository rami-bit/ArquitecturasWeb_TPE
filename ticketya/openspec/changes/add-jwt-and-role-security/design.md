# Design

## Context

Ver `proposal.md` para la motivación y los límites de alcance. El diseño vigente (§18) asigna la validación de firma y expiración al Gateway y mantiene autorización de dominio y ownership en los servicios. En E1, Catalog y Purchase interpretan `X-User-Id` y `X-User-Role`; el Gateway los reenvía actualmente sin validarlos. Sólo el Gateway publica un puerto en Compose.

## Goals / Non-Goals

**Goals:**

- Aceptar identidad de un proveedor externo configurable sin acoplar TicketYa a un IdP concreto.
- Evitar que un cliente externo suplante `sub` o rol mediante headers.
- Reusar las verificaciones actuales de rol y ownership en Catalog y Purchase.
- Mantener el acceso anónimo a las consultas que hoy son públicas y preservar `X-Correlation-Id`.

**Non-Goals:**

- Crear un servicio de identidad, cuentas, credenciales, login, emisión, renovación o revocación de tokens.
- Reescribir las reglas de negocio de eventos, compras, devoluciones o cancelaciones.
- Implementar autorización de endpoints QR para `ACCESS_STAFF`; corresponde a E2-05.
- Agregar una infraestructura de identidad, base de datos o broker al Compose del producto.

## Decisions

### 1. El proveedor externo emite los tokens; TicketYa actúa como Resource Server

El proveedor de identidad del diagrama del PDF emite el JWT. El Gateway valida firma, expiración y presencia/formato de los claims; la ubicación de las claves públicas y el issuer se inyectan por configuración de entorno. Se usará verificación asimétrica con claves públicas obtenidas del JWKS del proveedor, sin guardar claves privadas ni secretos de firma en Git.

El contrato del token usa `sub` como UUID del usuario y `roles` como una lista con exactamente un valor de `CLIENT`, `ORGANIZER`, `ADMIN` o `ACCESS_STAFF`. Un rol por token refleja el modelo actual de un solo `X-User-Role` y evita que el Gateway tenga que elegir entre roles o que cada servicio migre a evaluar conjuntos. Una persona con más de un perfil obtiene tokens separados.

Se elige un Resource Server estándar de Spring Security sobre validación criptográfica propia. Es la opción más simple que maneja firma, expiración y actualización de claves de un proveedor externo; crear un emisor local agregaría credenciales, almacenamiento y políticas no requeridos por el PDF.

### 2. El Gateway valida autenticación; los servicios conservan autorización de dominio

Las rutas protegidas requieren `Authorization: Bearer`. Una lista explícita mantiene anónimos `GET /api/events`, `GET /api/events/{id}` y `GET /api/inventory/events/{eventId}/availability`. Los healthchecks necesarios para Compose siguen accesibles para las sondas existentes; el acceso de administración de Actuator no se amplía.

En toda solicitud API enrutada, el Gateway descarta los `X-User-Id` y `X-User-Role` entrantes y consume el header `Authorization` sin reenviar el token Bearer a los servicios. Si valida un JWT, deriva de `sub` y del único rol los headers internos de identidad; si la ruta es pública y no hay token, no propaga identidad. Así Catalog, Inventory y Purchase no reciben identidad elegida por el cliente. El Gateway resuelve autenticación, pero no decide permisos de dominio.

No se agrega una segunda validación JWT en cada servicio: todos permanecen en la red interna de Compose, sin puertos publicados, y el Gateway es el único punto de entrada externo. Los comandos `/internal/**` continúan protegidos por su contrato de red y no pasan por el Gateway.

La matriz de autorización conserva los contratos de dominio existentes: `ADMIN` gestiona lugares y ejecuta suspensión/cancelación/reanudación de eventos; `ORGANIZER` y `ADMIN` acceden a la gestión de eventos, con ownership del `sub` para organizadores; Purchase conserva el acceso de `CLIENT` y `ADMIN` definido por su contrato, con ownership del `sub` para clientes; las devoluciones se limitan al titular y sus decisiones administrativas a `ADMIN`; la cancelación masiva requiere `ADMIN`. `ACCESS_STAFF` no obtiene permisos sobre esas operaciones y espera la capacidad de acceso QR.

### 3. Rechazos y superficies OpenAPI explícitos

El Gateway responde `401` para token ausente o inválido en una ruta protegida; los servicios responden `403` para rol insuficiente y conservan los `404` existentes que ocultan recursos ajenos. Los errores previos al upstream mantienen `X-Correlation-Id` y el formato de problema ya usado por el Gateway.

`docs/openapi.yaml` declara un esquema HTTP Bearer y lo aplica operación por operación. Las consultas públicas declaran seguridad vacía; los contratos internos siguen identificados como internos y no se publican por Gateway. Postman usa una variable de token emitido por el IdP configurado, no headers de identidad.

## Risks / Trade-offs

- **El IdP no está disponible o su configuración falta** → La validación requiere claves públicas configuradas. Documentar las variables necesarias; las pruebas de integración usan un emisor y claves de prueba controlados, sin depender del servicio externo.
- **El cambio rompe clientes E1 que enviaban headers** → No se conserva un fallback de headers, porque permitirlo anularía la autenticación. Actualizar OpenAPI, Postman y README en la misma épica.
- **Un componente dentro de la red interna podría falsificar headers de identidad** → La frontera de confianza es la red privada y el Gateway público; los servicios no publican puertos. No se agrega mTLS o una malla de servicios, fuera de la escala didáctica actual.
- **Los roles del proveedor no coinciden con TicketYa** → Exigir el claim `roles` y una sola etiqueta exacta del vocabulario acordado; documentar el mapeo en la configuración del IdP.
- **Los tokens contienen datos sensibles** → El Gateway y los servicios no registran el token ni claves; auditoría conserva identidad/correlation ID, no credenciales.

## Migration Plan

1. Configurar el issuer y el endpoint JWKS del IdP mediante variables de entorno; configurar el proveedor para emitir `sub` UUID y un único rol TicketYa.
2. Publicar el Gateway con validación JWT y propagación de identidad derivada; no activar compatibilidad temporal con `X-User-*`.
3. Actualizar operaciones protegidas de OpenAPI, colección Postman y README para usar Bearer; conservar las operaciones públicas.
4. Verificar roles, ownership, errores `401`/`403`, headers falsificados y flujo a través del Gateway antes de considerar el cambio listo.

El rollback no debe reabrir el acceso por headers. En una reversión de código se conserva el Gateway como único punto de entrada y se trata la versión anterior sin autenticación como no apta para exponer fuera del entorno local.
