# Spec Delta

## MODIFIED Requirements

### Requirement: Enrutar las rutas de catálogo por el Gateway

El Gateway SHALL exponer las rutas de Catalog Service bajo `/api/**` y reenviarlas a la URL base configurada sin quitar, agregar ni reescribir el prefijo `/api`. SHALL preservar método, path, query params y body. En operaciones protegidas SHALL consumir la autenticación Bearer y propagar únicamente la identidad derivada de claims validados, no los encabezados de identidad enviados por el cliente. Trazabilidad: PDF-NF-GATEWAY y PDF-NF-SECURITY.

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

### Requirement: Reenviar la identidad provisional sin autorizar en el Gateway

Para las operaciones protegidas de Entrega 2, el Gateway SHALL validar el JWT, SHALL eliminar cualquier `X-User-Id` y `X-User-Role` recibido del cliente y SHALL propagar la identidad derivada de `sub` y roles validados. El Gateway SHALL NOT decidir autorización de dominio; los servicios SHALL seguir validando rol y ownership. Las consultas públicas SHALL seguir permitiendo solicitudes sin identidad. Trazabilidad: PDF-NF-SECURITY.

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

### Requirement: Enrutar las órdenes de compra por el Gateway

El Gateway SHALL enrutar las solicitudes públicas bajo `/api/orders/**` hacia Purchase Service, evaluando esa ruta antes de la ruta de catálogo `/api/**`. SHALL preservar método, path, query params, body e `Idempotency-Key`; SHALL validar la autenticación Bearer y propagar a Purchase la identidad derivada de claims validados, sin confiar en headers de identidad externos. Trazabilidad: PDF-F4, PDF-NF-GATEWAY y PDF-NF-SECURITY.

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
