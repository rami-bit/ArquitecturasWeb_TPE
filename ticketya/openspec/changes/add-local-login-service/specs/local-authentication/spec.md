# Spec Delta

## Purpose

Permite iniciar sesión con cuentas fijas de demostración y obtener un JWT firmado que identifica al usuario y su único rol ante el Gateway, sin introducir registro ni administración de cuentas.

## ADDED Requirements

### Requirement: Autenticar cuentas fijas

Auth Service SHALL autenticar únicamente cuentas fijas con identificadores estables y distintos para `CLIENT`, `ORGANIZER`, `ADMIN` y `ACCESS_STAFF`. SHALL verificar una contraseña almacenada como hash y SHALL derivar identidad y rol de la configuración del servidor. Trazabilidad: PDF-NF-SECURITY.

#### Scenario: Credenciales correctas
- **WHEN** una cuenta habilitada envía usuario y contraseña correctos a `POST /api/auth/login`
- **THEN** el servicio responde `200` con un token de acceso Bearer y su vencimiento

#### Scenario: Usuario o contraseña incorrectos
- **WHEN** se presenta un usuario desconocido o una contraseña incorrecta
- **THEN** el servicio responde `401` con un mensaje genérico y no emite un token

#### Scenario: Identidad o rol arbitrarios en el pedido
- **WHEN** la solicitud incluye un `sub`, rol u otros campos no admitidos
- **THEN** el servicio rechaza la solicitud y no permite elegir una identidad o elevar permisos

### Requirement: Emitir JWT compatibles con el Gateway

Auth Service SHALL firmar los tokens con una clave privada propia y emitir `iss`, `sub` UUID, `iat`, `exp` y `roles` como arreglo de exactamente un rol permitido. SHALL publicar la clave pública correspondiente para validación del Gateway sin divulgar la privada. Trazabilidad: PDF-NF-SECURITY.

#### Scenario: Token de cliente válido
- **WHEN** una cuenta `CLIENT` inicia sesión
- **THEN** el JWT contiene su UUID estable y sólo `CLIENT`, y el Gateway lo acepta hasta su expiración

#### Scenario: Roles separados
- **WHEN** inician sesión cuentas de organizador, administrador y personal de acceso
- **THEN** cada token contiene únicamente el rol asignado a esa cuenta y el Gateway aplica sus permisos vigentes

#### Scenario: Clave privada no expuesta
- **WHEN** un consumidor consulta el material público de verificación
- **THEN** obtiene sólo claves públicas y ningún secreto de firma

### Requirement: Mantener identidad y firma estables entre reinicios

Auth Service SHALL conservar los UUID de las cuentas y la clave de firma entre reinicios normales del despliegue local, para que un token no vencido continúe validándose. Trazabilidad: PDF-NF-SECURITY y PDF-NF-DEPLOYMENT.

#### Scenario: Reinicio del emisor
- **GIVEN** un token vigente emitido antes del reinicio
- **WHEN** Auth Service reinicia sin eliminar sus datos persistentes
- **THEN** el Gateway sigue validando el token con la misma clave pública

### Requirement: Limitar la sesión al token de acceso

El login SHALL entregar sólo un token de acceso de duración acotada. Auth Service no SHALL ofrecer registro, recuperación de contraseña, refresh token, endpoint de logout ni revocación. El token emitido conserva validez hasta `exp`, incluso si el cliente descarta su copia.

#### Scenario: Token vencido
- **WHEN** un usuario intenta una operación protegida después de `exp`
- **THEN** el Gateway responde `401` y el usuario puede iniciar sesión nuevamente

#### Scenario: Descartar el token sin revocación
- **WHEN** un cliente descarta su copia de un JWT aún vigente
- **THEN** Auth Service no cambia su validez y el Gateway lo aceptaría si se presentara nuevamente antes de `exp`

### Requirement: No revelar credenciales ni tokens en registros

Auth Service SHALL registrar el resultado y la correlación de intentos de login sin registrar contraseñas, hashes, JWT completos ni material privado de firma. Trazabilidad: PDF-NF-SECURITY y PDF-NF-TRACEABILITY.

#### Scenario: Login fallido registrado
- **WHEN** una autenticación falla
- **THEN** el registro permite seguir el intento por correlation ID sin contener contraseña ni token
