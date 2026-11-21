# Spec Delta

## Purpose

Permite entregar por email los avisos relevantes de TicketYa a cada cliente, conservar evidencia del resultado y procesar reintentos sin multiplicar avisos por una misma operación de negocio.

## ADDED Requirements

### Requirement: Registrar un email de contacto propio

Notification Service SHALL permitir a un `CLIENT` autenticado registrar y consultar su email mediante `PUT` y `GET /api/notifications/me/email`. La identidad SHALL provenir del `sub` validado; el cuerpo SHALL contener sólo un email válido y no un `customerId`. Trazabilidad: PDF-F11 y PDF-NF-SECURITY.

#### Scenario: Cliente registra su email

- **WHEN** un `CLIENT` envía un email válido por el Gateway
- **THEN** el servicio guarda o reemplaza sólo su contacto y devuelve la dirección registrada

#### Scenario: Cliente consulta su email sin haberlo registrado

- **WHEN** un `CLIENT` sin contacto consulta su email
- **THEN** el servicio responde `200` con email nulo sin crear un contacto

#### Scenario: Identidad ajena o rol no autorizado

- **WHEN** un usuario intenta informar otro `customerId`, no presenta JWT válido o tiene un rol distinto de `CLIENT`
- **THEN** se rechaza con `400`, `401` o `403` según el caso, sin leer ni modificar el contacto ajeno

### Requirement: Avisar una compra confirmada

El sistema SHALL generar un aviso por email al titular cuando una orden pasa por primera vez a `CONFIRMED` con entradas emitidas. No SHALL enviarlo para órdenes rechazadas, pendientes o compensadas. Trazabilidad: PDF-F11.

#### Scenario: Compra confirmada

- **WHEN** Purchase confirma una orden y emite sus entradas
- **THEN** el titular con email registrado recibe un aviso que identifica la orden y el evento, sin incluir medio de pago ni payload QR

#### Scenario: Reconciliación repetida

- **WHEN** la misma orden confirmada se consulta o reconcilia otra vez
- **THEN** no se crea un segundo aviso de confirmación para ese cliente y orden

### Requirement: Avisar cancelación y postergación del evento

El sistema SHALL avisar una vez por cliente y evento cuando Catalog confirme su cancelación, y una vez por cliente y revisión de fecha cuando confirme una postergación. El aviso SHALL distinguir ambos hechos e informar la fecha anterior y nueva cuando corresponda. Trazabilidad: PDF-F7 y PDF-F11.

#### Scenario: Evento cancelado con varias entradas por comprador

- **WHEN** Catalog confirma la cancelación de un evento con varias entradas del mismo cliente
- **THEN** ese cliente recibe un solo aviso de cancelación y el mensaje no afirma que el reintegro terminó si aún está pendiente

#### Scenario: Evento postergado

- **WHEN** Catalog confirma una postergación de la revisión `3` a la `4`
- **THEN** cada comprador con email recibe una vez la fecha anterior y la nueva, identificadas con la revisión `4`

#### Scenario: Reintento de cancelación o postergación

- **WHEN** se reintenta el procesamiento del mismo evento y revisión
- **THEN** no se crean avisos adicionales para el mismo cliente

### Requirement: Avisar publicación de artista favorito en la ciudad del cliente

El sistema SHALL avisar a cada cliente con email registrado cuando se publique por primera vez un evento futuro cuyo participante coincida con un artista favorito y cuya ciudad coincida con su preferencia. SHALL comparar sin distinguir mayúsculas ni espacios extremos. Trazabilidad: PDF-F11.

#### Scenario: Publicación coincidente

- **WHEN** se publica un evento con un artista favorito en la ciudad del cliente
- **THEN** el cliente recibe un solo aviso que identifica evento, artista y fecha vigente

#### Scenario: Publicación no coincidente o repetida

- **WHEN** el artista o la ciudad no coinciden, o se repite idempotentemente la publicación
- **THEN** no se crea un aviso nuevo para ese cliente

### Requirement: Conservar estado e idempotencia de la entrega

Notification Service SHALL persistir un registro único por tipo, operación fuente y cliente, con estado `PENDING`, `SENT`, `FAILED` o `SKIPPED_NO_CONTACT`, intentos y `correlationId`. `SENT` SHALL significar aceptación por el servidor SMTP, no recepción en una casilla externa. Trazabilidad: PDF-F11 y PDF-NF-TRACE.

#### Scenario: Evento interno duplicado

- **WHEN** el servicio recibe dos veces el mismo identificador de operación para el mismo cliente
- **THEN** conserva un solo registro de aviso y no vuelve a enviar uno en estado `SENT`

#### Scenario: Cliente sin email

- **WHEN** se procesa un aviso para un cliente que no registró email
- **THEN** se registra `SKIPPED_NO_CONTACT` y no se inventa un destinatario

#### Scenario: SMTP acepta el mensaje

- **WHEN** el servidor SMTP acepta un mensaje pendiente
- **THEN** el registro pasa a `SENT` y conserva la fecha de aceptación

### Requirement: Aislar fallas del proveedor y recuperar avisos pendientes

El fallo SMTP o de Notification Service SHALL NOT revertir una compra, publicación, postergación o cancelación ya confirmada. Los eventos pendientes SHALL poder reintentarse con tiempo de espera y límite configurables, sin perder el vínculo con la operación fuente. Trazabilidad: PDF-NF-RESILIENCE.

#### Scenario: SMTP no disponible

- **WHEN** falla el envío de un aviso pendiente
- **THEN** la operación de negocio conserva su resultado y el aviso queda `FAILED` o pendiente para reintento, con causa y `correlationId` observables

#### Scenario: Notification Service no disponible al producir el evento

- **WHEN** Catalog o Purchase confirma una operación mientras Notification Service no responde
- **THEN** la operación termina y su evento pendiente puede reenviarse cuando el servicio se recupere
