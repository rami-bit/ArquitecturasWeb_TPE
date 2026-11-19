# Spec Delta

## Purpose

Permitir al personal de acceso validar entradas QR en el ingreso al evento, una sola vez por entrada.

## ADDED Requirements

### Requirement: Validar una entrada en el acceso

Ticket Service SHALL permitir a `ACCESS_STAFF` validar el QR de una entrada para un evento. SHALL verificar la firma HMAC del payload, que la entrada exista, que corresponda al evento solicitado y que esté emitida y sin usar; en ese caso SHALL marcarla `USED` con fecha, puerta y validador, y responder `200 ADMITTED`. Trazabilidad: PDF-F8.

#### Scenario: Primer escaneo válido

- **GIVEN** una entrada `ISSUED` del evento
- **WHEN** `ACCESS_STAFF` valida su QR para ese evento
- **THEN** recibe `200` con `result=ADMITTED` y la entrada queda `USED`

#### Scenario: Segundo escaneo

- **GIVEN** una entrada ya utilizada
- **WHEN** se vuelve a escanear
- **THEN** recibe `409` con `reason=ALREADY_USED`, `usedAt` y `usedGate`

#### Scenario: Entrada de otro evento

- **WHEN** se escanea una entrada válida para un evento distinto
- **THEN** recibe `422` con `reason=WRONG_EVENT` y la entrada no se consume

#### Scenario: Entrada revocada

- **GIVEN** una entrada revocada por devolución o cancelación
- **WHEN** se escanea
- **THEN** recibe `409` con `reason=REVOKED`

#### Scenario: QR falsificado o inexistente

- **WHEN** se escanea un payload con firma inválida, formato incorrecto o ticket inexistente
- **THEN** recibe `404` con `reason=UNKNOWN_TICKET` y ninguna entrada cambia

### Requirement: Impedir el doble ingreso bajo concurrencia

La marca de uso SHALL ser una única escritura condicional (`UPDATE ... WHERE status = 'ISSUED'`), de modo que entre escaneos simultáneos del mismo QR, aun desde varias instancias del servicio, exactamente uno resulte `ADMITTED`. SHALL existir además una restricción de unicidad que impida más de un registro de ingreso aceptado por entrada. Trazabilidad: PDF-F8.

#### Scenario: Escaneos simultáneos

- **WHEN** se envían 25 validaciones simultáneas del mismo QR
- **THEN** una responde `200` y 24 responden `409 ALREADY_USED`, y existe un único registro `ADMITTED`

### Requirement: Registrar y consultar los intentos de ingreso

Cada intento de validación, aceptado o rechazado, SHALL registrarse con evento, entrada (si es conocida), personal, puerta, resultado y correlation id, y publicarse en la bitácora centralizada. `ACCESS_STAFF` y `ADMIN` SHALL poder consultar los intentos de un evento con paginación y totales por resultado. Trazabilidad: PDF-F8 y PDF-NF-TRACE.

#### Scenario: Consultar intentos

- **WHEN** un `ADMIN` consulta los intentos de un evento con un ingreso y un reingreso
- **THEN** obtiene `admitted=1`, `rejected=1` y ambos intentos, el más reciente primero

### Requirement: Autorizar el control de acceso por rol

Sólo `ACCESS_STAFF` SHALL validar entradas. `ACCESS_STAFF` y `ADMIN` SHALL consultar los intentos. Cualquier otro rol SHALL recibir `403` sin cambios en las entradas. Trazabilidad: PDF-NF-SECURITY.

#### Scenario: Rol no autorizado

- **WHEN** un `CLIENT`, `ORGANIZER` o `ADMIN` intenta validar un QR
- **THEN** recibe `403` y la entrada sigue `ISSUED`
