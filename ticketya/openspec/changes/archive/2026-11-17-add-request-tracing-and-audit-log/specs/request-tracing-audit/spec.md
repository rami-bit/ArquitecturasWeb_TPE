# Spec Delta

## Purpose

Registrar cada solicitud HTTP y cada operación crítica de TicketYa con un identificador común que permita seguirlas a través de los microservicios.

## ADDED Requirements

### Requirement: Registrar cada solicitud HTTP

El Gateway y cada microservicio SHALL registrar una línea por solicitud HTTP con método, endpoint (path y query), usuario (`sub` o `X-User-Id` derivado), rol, código de respuesta, duración en milisegundos y `X-Correlation-Id`. El Gateway SHALL registrar también las solicitudes que rechaza con `401` o `403` sin invocar un servicio. Las comprobaciones de salud MAY registrarse en un nivel de detalle inferior. Trazabilidad: PDF-NF-TRACE.

#### Scenario: Solicitud autenticada exitosa

- **WHEN** un `ADMIN` con JWT válido solicita una operación que responde `200`
- **THEN** el Gateway registra método, path, `status=200`, duración, `userId` igual al `sub`, `role=ADMIN` y el `X-Correlation-Id`

#### Scenario: Solicitud rechazada por el Gateway

- **WHEN** una solicitud sin token o con un rol no autorizado es rechazada con `401` o `403`
- **THEN** el Gateway registra la línea con el código de rechazo y, si el token era válido, el usuario y el rol

#### Scenario: Solicitud atendida por un servicio

- **WHEN** un servicio atiende una solicitud propagada por el Gateway
- **THEN** registra la línea con la identidad derivada y el mismo `X-Correlation-Id`, y todas sus líneas de log de esa solicitud incluyen ese identificador

### Requirement: Registrar las operaciones críticas en una bitácora centralizada

Purchase, Catalog y Ticket SHALL registrar en Audit Service un evento por cada compra, reconciliación, solicitud/decisión/ejecución de devolución, cancelación de evento, cambio de estado de evento, emisión/revocación de entradas y validación de ingreso, incluidos los resultados fallidos. Cada evento SHALL incluir un `eventId` único, el `X-Correlation-Id` de la operación, servicio, operación, resultado, actor, recurso, detalles y fecha. Trazabilidad: PDF-NF-TRACE.

#### Scenario: Compra confirmada

- **WHEN** una compra se confirma
- **THEN** Purchase registra `PURCHASE_ORDER` con resultado `CONFIRMED` y Ticket registra `TICKETS_ISSUED` con el mismo `X-Correlation-Id`

#### Scenario: Compra rechazada o fallida

- **WHEN** una compra termina con pago rechazado, orden compensada o error de validación o de dependencia
- **THEN** Purchase registra `PURCHASE_ORDER` con resultado `REJECTED`, `COMPENSATED` o `FAILED` y el código HTTP en los detalles

#### Scenario: Cancelación de evento

- **WHEN** un `ADMIN` cancela un evento con ventas
- **THEN** la bitácora contiene, con un único `X-Correlation-Id`, eventos de Purchase, Catalog y Ticket

### Requirement: Consultar la bitácora por operación

Audit Service SHALL permitir a `ADMIN` buscar eventos por correlation id, servicio, operación, resultado, recurso, actor y rango de fechas con paginación, y obtener la traza completa de un `X-Correlation-Id` en orden cronológico. Otros roles SHALL recibir `403`. Trazabilidad: PDF-NF-TRACE y PDF-NF-SECURITY.

#### Scenario: Seguir una operación

- **WHEN** un `ADMIN` consulta la traza de un `X-Correlation-Id` existente
- **THEN** recibe los eventos de todos los servicios participantes ordenados por fecha y la lista de servicios

#### Scenario: Identificador inexistente

- **WHEN** un `ADMIN` consulta un `X-Correlation-Id` sin eventos
- **THEN** recibe `404` en `application/problem+json`

#### Scenario: Rol no autorizado

- **WHEN** un `CLIENT`, `ORGANIZER` o `ACCESS_STAFF` consulta la bitácora
- **THEN** recibe `403`

### Requirement: La auditoría no afecta la operación de negocio

El envío de eventos SHALL ser asíncrono, con timeouts cortos y reintentos acotados, e idempotente por `eventId`. Si Audit Service no está disponible, la operación de negocio SHALL completarse igual y el evento SHALL quedar en el log local del productor. Trazabilidad: PDF-NF-RESILIENCE.

#### Scenario: Audit Service caído

- **GIVEN** Audit Service detenido
- **WHEN** se realizan compras
- **THEN** las compras responden como siempre y el productor registra en su log que el evento quedó sólo localmente

#### Scenario: Reintento del envío

- **WHEN** Audit Service recibe dos veces el mismo `eventId`
- **THEN** conserva un único registro y responde `202` con `duplicate: true`
