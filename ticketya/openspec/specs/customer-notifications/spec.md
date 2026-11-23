# customer-notifications Specification

## Purpose

Informar al cliente, por email y push, el resultado de sus compras, los cambios de los eventos que compró y los eventos nuevos de sus artistas favoritos en su ciudad.

## Requirements

### Requirement: Notificar al comprador el resultado de su compra

Notification Service SHALL enviar al comprador una notificación por cada canal configurado (email y push) cuando una orden se confirma, incluso al confirmarse por reconciliación, y cuando una orden termina compensada. Trazabilidad: PDF-F11.

#### Scenario: Compra confirmada

- **WHEN** Purchase confirma una orden
- **THEN** el comprador recibe una notificación `ORDER_CONFIRMED` por `EMAIL` y otra por `PUSH`, con el nombre del evento y el identificador de la orden

#### Scenario: Orden compensada

- **WHEN** una orden pagada termina `COMPENSATED`
- **THEN** el comprador recibe una notificación `ORDER_CANCELLED` que informa el reintegro

### Requirement: Notificar a los compradores los cambios y la cancelación del evento

Cuando un evento se suspende, se reanuda o se cancela, Notification Service SHALL notificar a cada cliente con una orden pagada del evento, una vez por transición. Catalog SHALL informar la transición sólo después de confirmarla. Trazabilidad: PDF-F11 y PDF-F7.

#### Scenario: Evento cancelado

- **GIVEN** un evento con dos compradores distintos
- **WHEN** un `ADMIN` lo cancela
- **THEN** cada comprador recibe una notificación `EVENT_CANCELLED` por email y push

#### Scenario: Suspensión y reanudación

- **WHEN** un evento se suspende y luego se reanuda
- **THEN** sus compradores reciben `EVENT_SUSPENDED` y luego `EVENT_RESUMED`

#### Scenario: Transición rechazada

- **WHEN** Catalog rechaza una transición (por ejemplo, reanudar un evento que no está suspendido)
- **THEN** no se envía ninguna notificación

### Requirement: Notificar a los fans cuando su artista favorito publica en su ciudad

Cuando se publica un evento, Notification Service SHALL notificar a los clientes cuya ciudad coincide con la del lugar y que tienen como favorito a alguno de sus participantes, comparando sin distinguir mayúsculas ni espacios extremos. Trazabilidad: PDF-F11 y PDF-F10.

#### Scenario: Fan en la misma ciudad

- **GIVEN** un cliente con ciudad `Rosario` y artista favorito `Banda Uno`
- **WHEN** se publica en Rosario un evento con `Banda Uno`
- **THEN** el cliente recibe una notificación `EVENT_PUBLISHED`

#### Scenario: Sin coincidencia

- **WHEN** se publica un evento cuyo artista no figura entre los favoritos de un cliente, o en otra ciudad
- **THEN** ese cliente no recibe la notificación

### Requirement: Entregar de forma asíncrona, idempotente y resiliente

Los productores SHALL enviar los hechos sin bloquear ni hacer fallar la operación de negocio. Notification Service SHALL deduplicar cada hecho por su clave de negocio. SHALL reintentar con espera exponencial la resolución de destinatarios y cada entrega, y marcar `FAILED` lo que agote los intentos. Varias instancias SHALL procesar cada hecho y cada notificación una sola vez. Trazabilidad: PDF-NF-RESILIENCE y PDF-NF-ASYNC.

#### Scenario: Reenvío del productor

- **WHEN** Notification Service recibe dos veces el mismo hecho
- **THEN** responde `202` con `duplicate: true` y no duplica notificaciones

#### Scenario: Dependencia caída

- **GIVEN** Purchase no responde al resolver los compradores
- **WHEN** se procesa una cancelación
- **THEN** el hecho queda pendiente con el error registrado y se procesa al recuperarse la dependencia

#### Scenario: Pasarela caída

- **GIVEN** la pasarela de email/push falla
- **WHEN** se agotan los intentos de entrega
- **THEN** la notificación queda `FAILED` con el último error y la compra original no se ve afectada

#### Scenario: Procesamiento concurrente

- **WHEN** varias instancias procesan a la vez los mismos hechos pendientes
- **THEN** cada hecho genera exactamente una notificación por destinatario y canal

### Requirement: Consultar la bandeja propia

Un `CLIENT` SHALL consultar sus notificaciones, más recientes primero, con canal, estado y paginación, sin `customerId` en la ruta. Otros roles SHALL recibir `403`. Trazabilidad: PDF-F11 y PDF-NF-SECURITY.

#### Scenario: Cliente consulta su bandeja

- **WHEN** un `CLIENT` solicita `GET /api/notifications/me`
- **THEN** recibe sólo sus notificaciones

#### Scenario: Otro rol

- **WHEN** un `ORGANIZER`, `ADMIN` o `ACCESS_STAFF` solicita la bandeja
- **THEN** recibe `403`

### Requirement: Registrar un email de contacto propio

Notification Service SHALL permitir a un `CLIENT` registrar y consultar su dirección mediante `PUT` y `GET /api/notifications/me/email`. SHALL derivar el cliente del JWT validado por el Gateway y rechazar un `customerId` en el cuerpo. Trazabilidad: PDF-F11 y PDF-NF-SECURITY.

#### Scenario: Registrar o consultar un email

- **WHEN** un `CLIENT` registra una dirección válida y luego consulta su contacto
- **THEN** recibe la dirección guardada para su propia identidad

#### Scenario: Cliente sin contacto

- **WHEN** un `CLIENT` sin email registrado consulta su contacto
- **THEN** recibe `200` con email nulo

#### Scenario: Identidad ajena o email inválido

- **WHEN** un usuario envía un `customerId` en el cuerpo, una dirección inválida o un rol distinto de `CLIENT`
- **THEN** se rechaza la operación sin modificar contactos ajenos

### Requirement: Entregar el canal EMAIL por SMTP

Notification Service SHALL enviar por SMTP las notificaciones `EMAIL` de F11 al contacto registrado. SHALL marcar `SENT` sólo tras aceptación SMTP; si no existe contacto, SHALL conservar un estado explícito sin inventar un destinatario. El canal `PUSH` y la bandeja propios conservan su comportamiento actual. Trazabilidad: PDF-F11 y PDF-NF-RESILIENCE.

#### Scenario: Correo aceptado

- **WHEN** una notificación `EMAIL` pendiente tiene contacto y SMTP acepta el mensaje
- **THEN** el destinatario recibe el asunto y cuerpo correspondientes, y el registro pasa a `SENT` con fecha de aceptación

#### Scenario: Falta contacto

- **WHEN** una notificación `EMAIL` pendiente pertenece a un cliente sin email registrado
- **THEN** queda `SKIPPED_NO_CONTACT` y no se envía a ninguna dirección

#### Scenario: SMTP no disponible

- **WHEN** SMTP rechaza o no responde a un intento
- **THEN** se conserva la notificación para reintento con causa y `correlationId`, sin alterar la compra o el cambio de evento

### Requirement: Avisar la postergación de un evento

Notification Service SHALL generar `EVENT_POSTPONED` para cada comprador informado por el contrato interno existente, una vez por cambio confirmado. El mensaje SHALL incluir fecha anterior y nueva; el hecho SHALL conservar un identificador de cambio estable para deduplicación. Trazabilidad: PDF-F11.

#### Scenario: Postergación confirmada

- **WHEN** Catalog confirma una postergación y envía el hecho con ambas fechas
- **THEN** cada comprador recibe una notificación con la fecha anterior y la nueva por los canales configurados

#### Scenario: Reintento del mismo cambio

- **WHEN** Notification Service recibe otra vez el mismo identificador de postergación
- **THEN** responde como duplicado y no crea un segundo aviso para ese comprador y canal
