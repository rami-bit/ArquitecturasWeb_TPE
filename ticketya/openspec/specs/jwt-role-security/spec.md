# jwt-role-security Specification

## Purpose
Permite autenticar las operaciones protegidas de TicketYa con JWT firmado y autorizar cada actor según su rol y ownership, manteniendo públicas las consultas definidas como anónimas.

## Requirements

### Requirement: Validar identidad JWT emitida por un proveedor externo

Las operaciones protegidas SHALL aceptar únicamente un JWT firmado y vigente, con `sub` y exactamente un rol válido para TicketYa. Trazabilidad: PDF-NF-SECURITY.

#### Scenario: Identidad válida

- **WHEN** una solicitud protegida presenta un JWT firmado por el proveedor de identidad configurado, no vencido, con `sub` UUID y un único rol válido
- **THEN** el sistema identifica al actor por `sub` y sus roles por el claim de roles

#### Scenario: Token ausente, inválido o vencido

- **WHEN** una solicitud protegida presenta un token ausente, malformado, con firma inválida, vencido o sin claims de identidad válidos
- **THEN** el Gateway responde `401` y no invoca el servicio de dominio

### Requirement: Autorizar operaciones protegidas por rol y ownership

El sistema SHALL aplicar a la identidad validada las reglas de rol y ownership definidas por cada capacidad, sin conceder permisos por un rol no autorizado. Trazabilidad: PDF-NF-SECURITY, PDF-F1, PDF-F4 y PDF-F7.

#### Scenario: Rol insuficiente

- **GIVEN** un JWT válido cuyo rol no está autorizado para la operación solicitada
- **WHEN** el actor invoca la operación
- **THEN** el sistema responde `403` sin ejecutar el cambio de dominio

#### Scenario: El organizador conserva el ownership

- **GIVEN** un JWT válido con rol `ORGANIZER`
- **WHEN** intenta gestionar un evento cuyo organizador no coincide con el `sub`
- **THEN** Catalog rechaza la operación conforme a las reglas vigentes de ownership

#### Scenario: El cliente conserva el ownership de sus compras

- **GIVEN** una orden o devolución perteneciente a otro `sub`
- **WHEN** un JWT válido con rol `CLIENT` intenta consultarla
- **THEN** Purchase responde `404` sin revelar la existencia ni los datos del recurso

#### Scenario: ACCESS_STAFF no recibe permisos administrativos

- **GIVEN** un JWT válido con rol `ACCESS_STAFF`
- **WHEN** el actor intenta una operación reservada a `ADMIN`
- **THEN** el sistema responde `403`

### Requirement: Mantener públicas las consultas anónimas existentes

El sistema SHALL permitir sin JWT las consultas de eventos publicados y disponibilidad que las capacidades vigentes definen como públicas. Trazabilidad: PDF-F4 y PDF-F9.

#### Scenario: Buscar eventos sin token

- **WHEN** un cliente sin JWT solicita `GET /api/events` o `GET /api/events/{id}`
- **THEN** Catalog procesa la consulta pública sin exigir autenticación

#### Scenario: Consultar disponibilidad sin token

- **WHEN** un cliente sin JWT solicita `GET /api/inventory/events/{eventId}/availability`
- **THEN** Inventory procesa la consulta sin exigir autenticación

### Requirement: Rechazar identidad externa falsificada

El sistema SHALL derivar la identidad de la solicitud protegida exclusivamente de los claims del JWT validado y SHALL NOT confiar en encabezados de identidad enviados por el cliente. Trazabilidad: PDF-NF-SECURITY.

#### Scenario: Ignorar encabezados de identidad suministrados por el cliente

- **GIVEN** un JWT válido cuyo `sub` identifica al cliente A
- **WHEN** el request también incluye `X-User-Id` o `X-User-Role` con valores distintos
- **THEN** Purchase y Catalog autorizan usando únicamente el `sub` y los roles del JWT

#### Scenario: Los encabezados no autentican por sí solos

- **WHEN** un request a una operación protegida contiene `X-User-Id` y `X-User-Role`, pero no un JWT válido
- **THEN** el Gateway responde `401`

### Requirement: No registrar tokens ni credenciales

El sistema SHALL NOT registrar el JWT completo, secretos de firma ni credenciales del proveedor de identidad. Trazabilidad: PDF-NF-SECURITY.

#### Scenario: Registrar una solicitud autenticada

- **WHEN** el sistema registra una solicitud que contiene un JWT
- **THEN** los logs no contienen el token completo ni secretos de autenticación
