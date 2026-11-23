# Spec Delta

## ADDED Requirements

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
