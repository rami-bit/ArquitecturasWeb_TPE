# Spec Delta

## ADDED Requirements

### Requirement: Enrutar la disponibilidad de inventario por el Gateway

El Gateway SHALL enrutar las solicitudes públicas de disponibilidad bajo `/api/inventory/**` hacia Inventory Service, evaluando esa ruta antes de la ruta de catálogo `/api/**`. SHALL preservar método, path, query params, headers y body, y SHALL NOT exponer el puerto interno de Inventory.

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
- **THEN** el único puerto HTTP publicado al host es el del Gateway e Inventory Service sólo es accesible dentro de la red interna

#### Scenario: Consultar disponibilidad sin identidad

- **WHEN** un cliente consulta la disponibilidad sin encabezados de identidad
- **THEN** el Gateway enruta la solicitud sin exigir autenticación

### Requirement: No publicar los comandos internos de inventario

El Gateway SHALL NOT enrutar los comandos internos de reserva, confirmación, liberación o consulta de reservas de Inventory. Esos contratos SHALL quedar accesibles sólo dentro de la red de servicios para Purchase Service.

#### Scenario: Cliente intenta invocar una reserva interna

- **WHEN** un cliente solicita `/internal/reservations` o cualquier ruta interna de Inventory a través del Gateway
- **THEN** el Gateway no la enruta a Inventory y responde como ruta no encontrada

#### Scenario: Purchase invoca Inventory dentro de la red

- **WHEN** Purchase Service llama al contrato interno de reservas usando el nombre de servicio de Compose
- **THEN** Inventory procesa la solicitud sin que ésta pase por el Gateway

### Requirement: Preservar respuestas y correlation ID de Inventory

El Gateway SHALL reenviar el status, content type, headers y body de Inventory, incluidos los errores `application/problem+json` y su campo `correlationId`. SHALL conservar o generar un `X-Correlation-Id` y devolverlo al cliente.

#### Scenario: Propagar un error de Inventory

- **WHEN** Inventory responde `404`, `409` o `503` en `application/problem+json`
- **THEN** el cliente recibe el mismo status, content type, body y valor de `correlationId`

#### Scenario: Propagar el correlation ID en disponibilidad

- **WHEN** un cliente envía `X-Correlation-Id: trace-availability-1`
- **THEN** Inventory recibe ese valor y el cliente lo recibe en la respuesta

#### Scenario: Inventory no disponible

- **WHEN** Inventory no responde o excede el tiempo de espera
- **THEN** el Gateway responde `503` o `504` con un cuerpo de problema y el `X-Correlation-Id` resuelto
