# Spec Delta

## Purpose

Provee una pasarela de pagos simulada, aislada en la red interna, que aprueba, rechaza, deja pendiente o reintegra operaciones de forma idempotente para que Purchase pueda completar y compensar compras sin cobros duplicados.

## ADDED Requirements

### Requirement: Cobrar una operación de forma idempotente

Payment Mock SHALL registrar un cobro por `orderId` de forma idempotente. La misma orden con el mismo monto, moneda y medio de pago SHALL devolver el pago existente; la misma orden con contenido distinto SHALL rechazarse como conflicto sin crear un segundo cobro.

#### Scenario: Primer cobro

- **GIVEN** una orden sin pago registrado
- **WHEN** Purchase solicita un cobro con monto, moneda, medio de pago y token de simulación
- **THEN** Payment registra el pago, devuelve su identificador y el estado simulado

#### Scenario: Repetir el cobro

- **GIVEN** un pago ya registrado para una orden
- **WHEN** Purchase repite el cobro con el mismo contenido
- **THEN** Payment devuelve el mismo pago con `200` sin registrar otro movimiento

#### Scenario: Reutilizar la orden con otro contenido

- **GIVEN** un pago registrado para una orden
- **WHEN** Purchase repite el cobro con otro monto, moneda o medio de pago
- **THEN** Payment responde `409` y conserva el pago original

### Requirement: Simular el resultado del pago

Payment Mock SHALL decidir el resultado del cobro a partir de un token de simulación documentado: aprobado, rechazado o pendiente. El resultado SHALL ser determinista y SHALL NOT depender de estado aleatorio no reproducible.

#### Scenario: Token aprobado

- **WHEN** el cobro incluye el token de simulación aprobado
- **THEN** Payment responde un pago `APPROVED`

#### Scenario: Token rechazado

- **WHEN** el cobro incluye el token de simulación rechazado
- **THEN** Payment responde un pago `REJECTED`

#### Scenario: Token pendiente

- **WHEN** el cobro incluye el token de simulación pendiente
- **THEN** Payment responde un pago `PENDING`

#### Scenario: Token desconocido

- **WHEN** el cobro incluye un token no documentado
- **THEN** Payment responde `400` como problema y no registra el pago

### Requirement: Consultar un pago

Payment Mock SHALL permitir consultar un pago por su identificador y por el `orderId`, devolviendo estado, monto, moneda, medio de pago y fechas.

#### Scenario: Consultar por orden

- **GIVEN** un pago registrado para una orden
- **WHEN** Purchase consulta el pago por `orderId`
- **THEN** Payment devuelve el pago con su estado actual

#### Scenario: Consultar un pago inexistente

- **WHEN** se consulta un pago o una orden sin pago
- **THEN** Payment responde `404` con `application/problem+json`

### Requirement: Reintegrar un pago aprobado

Payment Mock SHALL permitir reintegrar un pago `APPROVED` de forma idempotente. Un pago ya reintegrado SHALL devolver el mismo resultado; un pago rechazado o pendiente SHALL NOT poder reintegrarse.

#### Scenario: Reintegrar un pago aprobado

- **GIVEN** un pago `APPROVED`
- **WHEN** Purchase solicita el reintegro
- **THEN** Payment marca el pago como `REFUNDED` y devuelve el resultado

#### Scenario: Repetir el reintegro

- **GIVEN** un pago `REFUNDED`
- **WHEN** Purchase repite el reintegro
- **THEN** Payment devuelve el mismo pago sin duplicar el movimiento

#### Scenario: Reintegrar un pago no aprobado

- **GIVEN** un pago `REJECTED` o `PENDING`
- **WHEN** Purchase solicita el reintegro
- **THEN** Payment responde `409` y conserva el estado del pago

### Requirement: Aislar la pasarela simulada

Payment Mock SHALL ser un microservicio independiente con su propia base de datos y SHALL quedar accesible sólo dentro de la red interna de servicios. El Gateway SHALL NOT publicar sus rutas.

#### Scenario: Acceso externo bloqueado

- **WHEN** un cliente intenta acceder a `/internal/payments/**` a través del Gateway
- **THEN** el Gateway responde como ruta no encontrada

#### Scenario: Acceso interno permitido

- **WHEN** Purchase invoca Payment usando el nombre de servicio de Compose
- **THEN** Payment procesa la solicitud sin pasar por el Gateway

#### Scenario: Sin acceso a otras bases

- **WHEN** se inspecciona `payment_db`
- **THEN** sus tablas no contienen foreign keys hacia `purchase_db` ni datos completos de la orden

### Requirement: Errores consistentes y trazabilidad

Payment Mock SHALL responder los errores con `application/problem+json` incluyendo `status`, `title`, `detail`, `instance` y `correlationId`. SHALL conservar o generar `X-Correlation-Id` y devolverlo en todas las respuestas.

#### Scenario: Error de validación

- **WHEN** el cobro omite un campo obligatorio o incluye un monto no positivo
- **THEN** Payment responde `400` como problema con el correlation ID de la solicitud

#### Scenario: Error inesperado

- **WHEN** ocurre una falla interna
- **THEN** Payment responde un problema genérico sin trazas, credenciales ni detalles de la base

#### Scenario: Propagar el correlation ID

- **WHEN** una solicitud llega con `X-Correlation-Id: trace-payment-1`
- **THEN** Payment lo usa en sus registros y lo devuelve en la respuesta
