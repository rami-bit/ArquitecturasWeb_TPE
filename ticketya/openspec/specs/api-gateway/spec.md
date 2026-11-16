# api-gateway Specification

## Purpose

Expone las APIs públicas de catálogo e inventario a través de un único punto de entrada y garantiza que cada solicitud conserve un identificador de correlación y la identidad provisional durante la Entrega 1.

## Requirements

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
- **THEN** el único puerto HTTP publicado al host es el del Gateway y Catalog Service sólo es accesible dentro de la red interna

### Requirement: Generar o propagar X-Correlation-Id

El Gateway SHALL conservar un `X-Correlation-Id` entrante no vacío o generar un UUID v4 cuando falte o esté en blanco. SHALL enviar ese mismo valor a Catalog Service en la request y devolverlo en la response con un único valor.

#### Scenario: Propagar un identificador suministrado

- **WHEN** un cliente envía `X-Correlation-Id: trace-123`
- **THEN** Catalog Service recibe `trace-123` y el cliente recibe `trace-123` en la respuesta

#### Scenario: Generar un identificador ausente

- **WHEN** un cliente no envía `X-Correlation-Id`
- **THEN** el Gateway genera un UUID v4, lo envía a Catalog Service y devuelve el mismo UUID al cliente

#### Scenario: Generar ante un identificador vacío

- **WHEN** un cliente envía `X-Correlation-Id` vacío o compuesto sólo por espacios
- **THEN** el Gateway genera un UUID v4 y lo usa de punta a punta

#### Scenario: Evitar encabezados duplicados

- **WHEN** Catalog Service también devuelve `X-Correlation-Id`
- **THEN** la respuesta final contiene un solo valor, igual al identificador resuelto por el Gateway

### Requirement: Incluir el correlation ID en respuestas propias del Gateway

Toda respuesta producida por el Gateway, incluidas las rutas no encontradas y los errores anteriores a Catalog Service, SHALL incluir `X-Correlation-Id`.

#### Scenario: Ruta no encontrada

- **WHEN** un cliente solicita una ruta que el Gateway no enruta
- **THEN** la respuesta de error incluye `X-Correlation-Id`

### Requirement: Reenviar la identidad provisional sin autorizar en el Gateway

El Gateway SHALL eliminar de toda solicitud externa los encabezados `X-User-Id` y `X-User-Role` enviados por el cliente y SHALL NOT reenviar el JWT Bearer a servicios. Si la solicitud presenta un JWT válido, SHALL propagar únicamente la identidad derivada de `sub` y roles validados. Las operaciones protegidas SHALL exigir JWT; las consultas públicas SHALL permitir solicitudes anónimas y no propagar una identidad ausente. El Gateway SHALL NOT decidir autorización de dominio; los servicios SHALL seguir validando rol y ownership. Trazabilidad: PDF-NF-SECURITY.

#### Scenario: Reenviar identidad en una operación de gestión

- **WHEN** un cliente envía un JWT válido a una operación de gestión
- **THEN** Catalog Service recibe la identidad correspondiente a los claims validados y el Gateway no usa headers de identidad externos

#### Scenario: No inventar identidad ausente

- **WHEN** un cliente solicita una operación protegida sin un JWT válido
- **THEN** el Gateway responde `401` y no propaga una identidad al servicio

#### Scenario: Ignorar headers de identidad falsificados

- **GIVEN** un JWT válido con identidad distinta de la informada en `X-User-Id` o `X-User-Role`
- **WHEN** el cliente solicita una operación protegida
- **THEN** el Gateway propaga la identidad de los claims validados y descarta los valores externos

#### Scenario: Permitir consultas públicas sin identidad

- **WHEN** un cliente consulta un endpoint público sin JWT
- **THEN** el Gateway enruta la solicitud sin exigir autenticación

#### Scenario: Descartar identidad falsificada en una consulta pública

- **WHEN** un cliente consulta disponibilidad sin JWT e incluye `X-User-Id` o `X-User-Role`
- **THEN** el Gateway enruta la consulta anónima y no propaga esos encabezados ni agrega identidad

### Requirement: Preservar las respuestas de error de Catalog Service

El Gateway SHALL reenviar el status, content type, headers y body de Catalog Service, incluidos los errores `application/problem+json` y su campo `correlationId`.

#### Scenario: Propagar un error de dominio

- **WHEN** Catalog Service responde un error `403` o `404` en `application/problem+json`
- **THEN** el cliente recibe el mismo status, content type, body y valor de `correlationId`

### Requirement: Enrutar la disponibilidad de inventario por el Gateway

El Gateway SHALL enrutar las solicitudes públicas de disponibilidad bajo `/api/inventory/**` hacia Inventory Service, evaluando esa ruta antes de la ruta de catálogo `/api/**`. SHALL preservar método, path, query params, body y encabezados no relacionados con autenticación. SHALL consumir y no reenviar `Authorization` ni confiar en `X-User-Id` o `X-User-Role` externos; cuando un JWT válido esté presente, sólo SHALL propagar identidad derivada. SHALL NOT exponer el puerto interno de Inventory. Trazabilidad: PDF-NF-GATEWAY y PDF-NF-SECURITY.

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

- **WHEN** un cliente consulta la disponibilidad sin JWT y presenta encabezados de identidad arbitrarios
- **THEN** el Gateway enruta la consulta sin exigir autenticación y Inventory no recibe esos encabezados ni una identidad derivada

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

### Requirement: Publicar operaciones de devolución y cancelación
Gateway SHALL enrutar solicitudes del titular y decisiones administrativas a Purchase, y cancelación administrativa a Catalog/Purchase, sin exponer comandos internos de Ticket, Payment ni Inventory.

#### Scenario: Solicitud pública de devolución
- **WHEN** el titular solicita una devolución por la ruta pública
- **THEN** Gateway preserva identidad y correlation ID y devuelve la respuesta de Purchase

#### Scenario: Comando interno no público
- **WHEN** un cliente externo intenta invocar liberación de unidad o reintegro interno
- **THEN** Gateway responde ruta no encontrada

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
