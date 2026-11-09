# ticket-issuance Specification

## Purpose

Emite entradas digitales QR desde el snapshot de una orden confirmada,
conservando un unico ticket por unidad comprada y permitiendo reintentos seguros.

## Requirements

### Requirement: Emitir entradas para un item confirmado

Ticket SHALL aceptar un comando interno de Purchase para emitir una entrada por
cada unidad del item de orden confirmado. Cada entrada SHALL guardar
`orderId`, `orderItemId`, comprador, evento, fecha, lugar, sector y asiento
cuando corresponda. Ticket SHALL NOT guardar ni devolver un precio como dato
autoritativo.

#### Scenario: Emitir entradas para asientos numerados

- **GIVEN** Purchase confirma un item de orden con dos asientos distintos
- **WHEN** invoca el comando interno de emision
- **THEN** Ticket crea dos entradas con sus snapshots de asiento y responde `201`

#### Scenario: Emitir entradas para admision general

- **GIVEN** Purchase confirma tres unidades de un item de admision general
- **WHEN** invoca el comando interno con las tres unidades
- **THEN** Ticket crea tres entradas identificables individualmente sin fila ni numero de asiento

#### Scenario: Solicitud invalida

- **GIVEN** una solicitud con unidades duplicadas, campos requeridos ausentes o
  datos parciales de asiento
- **WHEN** se invoca el comando
- **THEN** Ticket responde `400 application/problem+json` y no persiste entradas

### Requirement: QR firmado con persistencia protegida

Cada entrada SHALL devolver un payload QR versionado, firmado con HMAC-SHA-256,
y una imagen PNG codificada en Base64. Ticket SHALL persistir solamente el hash
SHA-256 del payload, nunca el token QR en claro. La clave SHALL provenir de
configuracion externa y mantenerse estable entre instancias.

#### Scenario: Devolver un QR y conservar solo el hash

- **GIVEN** una unidad validamente emitida
- **WHEN** Purchase recibe la respuesta de Ticket
- **THEN** la respuesta contiene payload firmado e imagen PNG valida, y la base
  contiene solo el hash unico del payload

### Requirement: Idempotencia por orden e item

Ticket SHALL identificar una emision por `(orderId, orderItemId)` y comparar un
fingerprint canonico del comprador, snapshot y unidades. La repeticion identica
SHALL devolver las mismas entradas y los mismos QR sin crear filas adicionales.
La reutilizacion de la clave con contenido diferente SHALL responder `409`.

#### Scenario: Reintentar una emision identica

- **GIVEN** un item de orden ya emitido
- **WHEN** Purchase repite el mismo comando
- **THEN** Ticket responde `200` con los mismos tickets y QR y no duplica filas

#### Scenario: Reutilizar la clave para otro contenido

- **GIVEN** un item ya emitido para `(orderId, orderItemId)`
- **WHEN** una solicitud repite la clave con otros asientos o snapshot
- **THEN** Ticket responde `409` y conserva intacta la emision original

#### Scenario: Dos instancias emiten concurrentemente

- **GIVEN** dos instancias reciben al mismo tiempo el mismo comando
- **WHEN** ambas solicitudes se confirman en PostgreSQL
- **THEN** existe una sola emision por item, las respuestas representan los mismos
  tickets y no se crean entradas duplicadas

### Requirement: Comando interno y errores trazables

El comando de emision SHALL estar disponible solo bajo `/internal/**` dentro de
la red de servicios; SHALL NOT tener una ruta en el API Gateway. Todas las
respuestas SHALL propagar o generar `X-Correlation-Id`, y los errores SHALL usar
`application/problem+json` sin exponer claves, payloads QR ni trazas.

#### Scenario: Error de idempotencia

- **GIVEN** una clave reutilizada con distinto contenido
- **WHEN** Ticket rechaza la emision
- **THEN** responde `409 application/problem+json` con el mismo correlation ID

