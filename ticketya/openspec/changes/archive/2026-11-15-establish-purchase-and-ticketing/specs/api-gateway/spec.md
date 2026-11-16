# Spec Delta

## ADDED Requirements

### Requirement: Enrutar las órdenes de compra por el Gateway

El Gateway SHALL enrutar las solicitudes públicas bajo `/api/orders/**` hacia Purchase Service, evaluando esa ruta antes de la ruta de catálogo `/api/**`. SHALL preservar método, path, query params, headers, body e `Idempotency-Key`, sin decidir reglas de compra.

#### Scenario: Crear una orden a través del Gateway

- **WHEN** un cliente envía `POST /api/orders` con identidad e `Idempotency-Key`
- **THEN** Purchase recibe la misma ruta, headers, clave y body, y su respuesta vuelve al cliente

#### Scenario: Consultar una orden a través del Gateway

- **WHEN** un cliente solicita `GET /api/orders/{orderId}`
- **THEN** el Gateway la envía a Purchase sin reescribir la ruta

#### Scenario: Dar precedencia a las órdenes

- **WHEN** una solicitud comienza con `/api/orders/`
- **THEN** el Gateway la envía a Purchase y no a Catalog Service

#### Scenario: Conservar las rutas existentes

- **WHEN** un cliente solicita una ruta de catálogo o de disponibilidad de inventario
- **THEN** el Gateway mantiene el enrutamiento previo sin cambios

#### Scenario: No publicar Purchase directamente

- **WHEN** se inspecciona el despliegue del incremento
- **THEN** el único puerto HTTP publicado al host es el del Gateway y Purchase sólo es accesible dentro de la red interna

### Requirement: No publicar Payment Mock ni comandos internos de compra

El Gateway SHALL NOT enrutar las rutas internas de Payment Mock ni los comandos internos de Purchase. Esos contratos SHALL quedar accesibles sólo dentro de la red de servicios.

#### Scenario: Cliente intenta invocar Payment Mock

- **WHEN** un cliente solicita `/internal/payments` o cualquier ruta de Payment a través del Gateway
- **THEN** el Gateway no la enruta y responde como ruta no encontrada

#### Scenario: Purchase invoca Payment dentro de la red

- **WHEN** Purchase llama al contrato interno de Payment usando el nombre de servicio de Compose
- **THEN** Payment procesa la solicitud sin pasar por el Gateway

### Requirement: Preservar respuestas y correlation ID de Purchase

El Gateway SHALL reenviar el status, content type, headers y body de Purchase, incluidos los errores `application/problem+json` y su campo `correlationId`. SHALL conservar o generar un `X-Correlation-Id` y devolverlo al cliente.

#### Scenario: Propagar un error de compra

- **WHEN** Purchase responde `400`, `402`, `409` o `503` en `application/problem+json`
- **THEN** el cliente recibe el mismo status, content type, body y valor de `correlationId`

#### Scenario: Propagar el correlation ID en una compra

- **WHEN** un cliente envía `X-Correlation-Id: trace-order-1`
- **THEN** Purchase recibe ese valor y el cliente lo recibe en la respuesta

#### Scenario: Purchase no disponible

- **WHEN** Purchase no responde o excede el tiempo de espera
- **THEN** el Gateway responde `503` o `504` con un cuerpo de problema y el `X-Correlation-Id` resuelto
