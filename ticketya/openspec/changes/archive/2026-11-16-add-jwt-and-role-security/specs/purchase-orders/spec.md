# Spec Delta

## MODIFIED Requirements

### Requirement: Crear una orden de compra idempotente

Purchase SHALL crear una orden a partir de un `eventId`, uno o más ítems y un medio de pago, identificando al comprador por el `sub` del JWT validado y exigiendo una `Idempotency-Key`. La misma clave con el mismo contenido SHALL devolver la orden existente sin reservar, cobrar ni emitir de nuevo; la misma clave con contenido distinto SHALL rechazarse como conflicto. Trazabilidad: PDF-F4 y PDF-NF-SECURITY.

#### Scenario: Crear una compra nueva

- **WHEN** un cliente autenticado envía una orden válida con JWT, `Idempotency-Key` e ítems disponibles
- **THEN** Purchase devuelve la orden creada con su identificador y el estado resultante de la saga, asociada al `sub` autenticado

#### Scenario: Repetir la misma compra

- **GIVEN** una orden creada con una `Idempotency-Key`
- **WHEN** el mismo cliente repite la solicitud con la misma clave y el mismo contenido
- **THEN** Purchase devuelve la misma orden sin crear reservas, cobros ni tickets adicionales

#### Scenario: Reutilizar la clave con otro contenido

- **GIVEN** una orden creada con una `Idempotency-Key`
- **WHEN** el mismo cliente reutiliza la clave con otros ítems o evento
- **THEN** Purchase responde `409` con `application/problem+json` y no modifica la orden original

#### Scenario: Solicitud sin identidad o sin clave

- **WHEN** la solicitud no incluye un JWT válido o no incluye `Idempotency-Key`
- **THEN** el Gateway rechaza la identidad con `401`, o Purchase rechaza la clave faltante con `400`, y no se crea la orden

### Requirement: Consultar órdenes propias

Purchase SHALL permitir al comprador consultar una orden por identificador y listar sus propias órdenes, identificándolo mediante el `sub` del JWT validado. Un cliente SHALL NOT ver órdenes de otro comprador; un administrador SHALL poder consultarlas. Trazabilidad: PDF-F4 y PDF-NF-SECURITY.

#### Scenario: Consultar la propia orden

- **GIVEN** una orden creada por el cliente identificado por el `sub` del JWT validado
- **WHEN** el cliente solicita `GET /api/orders/{orderId}`
- **THEN** Purchase devuelve la orden con sus ítems, pago y tickets

#### Scenario: Consultar una orden ajena

- **GIVEN** una orden de otro comprador
- **WHEN** un cliente la solicita por identificador
- **THEN** Purchase responde `404` sin revelar su existencia

#### Scenario: Listar órdenes propias

- **WHEN** un cliente solicita `GET /api/orders` con un JWT válido
- **THEN** Purchase devuelve únicamente las órdenes asociadas a su `sub`, paginadas

#### Scenario: Administrador consulta órdenes

- **WHEN** un administrador autorizado solicita órdenes
- **THEN** Purchase permite consultar órdenes de cualquier comprador conforme a las reglas vigentes
