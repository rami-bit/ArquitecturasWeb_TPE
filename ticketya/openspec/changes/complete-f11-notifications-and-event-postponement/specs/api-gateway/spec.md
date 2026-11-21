# Spec Delta

## ADDED Requirements

### Requirement: Enrutar y proteger el contacto de notificaciones

El Gateway SHALL enrutar `/api/notifications/**` a Notification Service antes del comodín de Catalog y SHALL exigir JWT con rol `CLIENT` para `GET` y `PUT /api/notifications/me/email`. SHALL propagar sólo identidad derivada y `X-Correlation-Id`. Trazabilidad: PDF-F11 y PDF-NF-SECURITY.

#### Scenario: Cliente registra su email

- **WHEN** un `CLIENT` llama a `PUT /api/notifications/me/email` con JWT válido
- **THEN** Notification Service recibe la identidad derivada del token y el mismo correlation ID

#### Scenario: Otro rol o ausencia de token

- **WHEN** otro rol o un solicitante anónimo llama a la ruta de email
- **THEN** el Gateway responde `403` o `401` con `application/problem+json` sin invocar al servicio

#### Scenario: Contratos internos de notificación no publicados

- **WHEN** un cliente externo llama a `/internal/notifications` o a la búsqueda interna de destinatarios
- **THEN** el Gateway no enruta esos contratos

### Requirement: Proteger la postergación de un evento

El Gateway SHALL aceptar `PATCH /api/management/events/{id}/schedule` sólo con JWT de `ORGANIZER` o `ADMIN`, reenviar la identidad validada a Catalog y dejar la comprobación de ownership al servicio. Trazabilidad: PDF-F11 y PDF-NF-SECURITY.

#### Scenario: Organizador propio o administrador

- **WHEN** un `ORGANIZER` o `ADMIN` solicita la postergación por el Gateway
- **THEN** Catalog recibe la solicitud y decide autorización de dominio y validez de fecha

#### Scenario: Cliente intenta postergar

- **WHEN** un `CLIENT` intenta usar la operación de postergación
- **THEN** el Gateway responde `403` sin invocar Catalog

## MODIFIED Requirements

### Requirement: Enrutar las rutas de catálogo por el Gateway

El Gateway SHALL exponer las rutas de Catalog Service bajo `/api/**` y reenviarlas a la URL base configurada sin quitar, agregar ni reescribir el prefijo `/api`. SHALL preservar método, path, query params, body y encabezados no relacionados con autenticación. En operaciones protegidas SHALL consumir la autenticación Bearer y propagar únicamente la identidad derivada de claims validados, no los encabezados de identidad enviados por el cliente. Trazabilidad: PDF-NF-GATEWAY y PDF-NF-SECURITY.

#### Scenario: Enrutar una consulta pública

- **WHEN** un cliente solicita `GET /api/events` a través del Gateway sin JWT
- **THEN** Catalog Service recibe `GET /api/events` y su respuesta vuelve al cliente a través del Gateway

#### Scenario: Enrutar una consulta de gestión

- **WHEN** un cliente solicita `GET /api/management/events` a través del Gateway con un JWT válido
- **THEN** Catalog Service recibe la misma ruta y la identidad derivada del `sub` y roles validados

#### Scenario: Preservar query params y método

- **WHEN** un cliente envía una solicitud con parámetros de búsqueda o un método distinto de `GET`
- **THEN** Catalog Service recibe el mismo método y los mismos parámetros

#### Scenario: No publicar Catalog Service directamente

- **WHEN** se inspecciona el despliegue del incremento
- **THEN** el único puerto de API de TicketYa publicado al host es el del Gateway y Catalog Service sólo es accesible dentro de la red interna; la interfaz local de Mailpit no expone rutas de TicketYa

### Requirement: Enrutar la disponibilidad de inventario por el Gateway

El Gateway SHALL enrutar las solicitudes públicas de disponibilidad bajo `/api/inventory/**` hacia Inventory Service, evaluando esa ruta antes del comodín de catálogo `/api/**`. SHALL preservar método, path, query params, body y encabezados no relacionados con autenticación. SHALL NOT exponer el puerto interno de Inventory. Trazabilidad: PDF-NF-GATEWAY.

#### Scenario: Enrutar una consulta de disponibilidad

- **WHEN** un cliente solicita `GET /api/inventory/events/{eventId}/availability` a través del Gateway
- **THEN** Inventory Service recibe la misma ruta y su respuesta vuelve al cliente a través del Gateway

#### Scenario: Dar precedencia a la ruta de inventario

- **WHEN** una solicitud comienza con `/api/inventory/`
- **THEN** el Gateway la envía a Inventory Service y no a Catalog Service

#### Scenario: Conservar las rutas de catálogo

- **WHEN** un cliente solicita `GET /api/events` o una ruta de gestión de catálogo
- **THEN** el Gateway la envía a Catalog Service sin cambios de comportamiento

#### Scenario: Preservar query params y método

- **WHEN** un cliente envía una consulta de disponibilidad con parámetros o un método distinto de `GET`
- **THEN** Inventory Service recibe el mismo método y los mismos parámetros

#### Scenario: No publicar Inventory directamente

- **WHEN** se inspecciona el despliegue del incremento
- **THEN** la API de TicketYa sólo se publica por el Gateway e Inventory Service sólo es accesible dentro de la red interna

### Requirement: Enrutar las órdenes de compra por el Gateway

El Gateway SHALL enrutar las solicitudes públicas bajo `/api/orders/**` hacia Purchase Service, evaluando esa ruta antes de la ruta de catálogo `/api/**`. SHALL preservar método, path, query params, body, `Idempotency-Key` y encabezados no relacionados con autenticación; SHALL validar la autenticación Bearer y propagar a Purchase la identidad derivada de claims validados, sin confiar en headers de identidad externos. Trazabilidad: PDF-F4, PDF-NF-GATEWAY y PDF-NF-SECURITY.

#### Scenario: Crear una orden a través del Gateway

- **WHEN** un cliente envía `POST /api/orders` con JWT válido e `Idempotency-Key`
- **THEN** Purchase recibe la misma ruta, clave y body junto con la identidad derivada del `sub` y roles validados, y su respuesta vuelve al cliente

#### Scenario: Consultar una orden a través del Gateway

- **WHEN** un cliente con JWT válido solicita `GET /api/orders/{orderId}`
- **THEN** el Gateway envía a Purchase la misma ruta y la identidad derivada del token sin reescribir la ruta

#### Scenario: Dar precedencia a las órdenes

- **WHEN** una solicitud comienza con `/api/orders/`
- **THEN** el Gateway la envía a Purchase y no a Catalog Service

#### Scenario: Conservar las rutas existentes

- **WHEN** un cliente solicita una ruta de catálogo o de disponibilidad de inventario
- **THEN** el Gateway mantiene el enrutamiento previo sin cambios

#### Scenario: No publicar Purchase directamente

- **WHEN** se inspecciona el despliegue del incremento
- **THEN** la API de TicketYa sólo se publica por el Gateway y Purchase sólo es accesible dentro de la red interna
