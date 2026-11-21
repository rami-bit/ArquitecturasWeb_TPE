# Spec Delta

## ADDED Requirements

### Requirement: Enrutar el login local público

El Gateway SHALL permitir sin JWT `POST /api/auth/login` y SHALL enviarlo a Auth Service antes del comodín `/api/**` de Catalog, conservando método, cuerpo y `X-Correlation-Id`. SHALL preservar status y respuesta de login sin exponer rutas internas de Auth Service. Trazabilidad: PDF-NF-GATEWAY y PDF-NF-SECURITY.

#### Scenario: Login correcto por Gateway
- **WHEN** un usuario envía credenciales válidas a `POST /api/auth/login` sin Bearer
- **THEN** Auth Service recibe la solicitud y el Gateway devuelve el token y vencimiento con el mismo correlation ID

#### Scenario: Login incorrecto por Gateway
- **WHEN** Auth Service rechaza credenciales con `401`
- **THEN** el Gateway conserva el status, el cuerpo de error y el correlation ID sin convertirlo en un error de JWT ausente

#### Scenario: Ruta de login antes de Catalog
- **WHEN** el usuario invoca `POST /api/auth/login`
- **THEN** el Gateway enruta la solicitud a Auth Service y no a Catalog

#### Scenario: Rutas internas de Auth no publicadas
- **WHEN** un cliente externo intenta consultar el JWKS interno o material de firma mediante el Gateway
- **THEN** el Gateway no publica esas rutas

### Requirement: Conservar la protección de las demás APIs

El Gateway SHALL exigir JWT válido para las rutas protegidas existentes después de habilitar el login público; la solicitud de login no SHALL introducir confianza en encabezados externos `X-User-Id` o `X-User-Role`.

#### Scenario: Compra sin token tras habilitar login
- **WHEN** un cliente solicita `POST /api/orders` sin JWT
- **THEN** el Gateway responde `401` sin invocar Purchase

#### Scenario: Cabeceras de identidad en login
- **WHEN** la solicitud de login incluye `X-User-Id` o `X-User-Role`
- **THEN** el Gateway descarta esos encabezados antes de invocar Auth Service
