# api-gateway Specification

## Purpose

Expone el catálogo a través de un único punto de entrada y garantiza que cada solicitud conserve un identificador de correlación y la identidad provisional durante la Entrega 1.

## Requirements

### Requirement: Enrutar las rutas de catálogo por el Gateway

El Gateway SHALL exponer las rutas de Catalog Service bajo `/api/**` y reenviarlas a la URL base configurada sin quitar, agregar ni reescribir el prefijo `/api`. SHALL preservar método, path, query params, headers y body de la solicitud.

#### Scenario: Enrutar una consulta pública

- **WHEN** un cliente solicita `GET /api/events` a través del Gateway
- **THEN** Catalog Service recibe `GET /api/events` y su respuesta vuelve al cliente a través del Gateway

#### Scenario: Enrutar una consulta de gestión

- **WHEN** un cliente solicita `GET /api/management/events` a través del Gateway con identidad provisional
- **THEN** Catalog Service recibe la misma ruta y los mismos headers de identidad

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

El Gateway SHALL reenviar `X-User-Id` y `X-User-Role` tal como llegan y SHALL NOT inventarlos, modificarlos ni decidir autorización de dominio. Catalog Service SHALL seguir validando rol y ownership.

#### Scenario: Reenviar identidad en una operación de gestión

- **WHEN** un cliente envía `X-User-Id` y `X-User-Role` en una solicitud de gestión
- **THEN** Catalog Service recibe exactamente los mismos valores

#### Scenario: No inventar identidad ausente

- **WHEN** un cliente no envía encabezados de identidad
- **THEN** el Gateway no agrega identidad y Catalog Service decide el rechazo según sus reglas de dominio

#### Scenario: Permitir consultas públicas sin identidad

- **WHEN** un cliente consulta un endpoint público sin encabezados de identidad
- **THEN** el Gateway enruta la solicitud sin exigir identidad

### Requirement: Preservar las respuestas de error de Catalog Service

El Gateway SHALL reenviar el status, content type, headers y body de Catalog Service, incluidos los errores `application/problem+json` y su campo `correlationId`.

#### Scenario: Propagar un error de dominio

- **WHEN** Catalog Service responde un error `403` o `404` en `application/problem+json`
- **THEN** el cliente recibe el mismo status, content type, body y valor de `correlationId`
