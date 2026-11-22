# Design

## Context

Ver `proposal.md` y las specs para el alcance. Notification Service ya persiste disparadores y mensajes, deduplica por clave de negocio, resuelve compradores mediante `GET /internal/events/{eventId}/buyers` y fans mediante Preferences, reintenta entregas y expone la bandeja del cliente. Gateway ya enruta `/api/notifications/**` sólo para `CLIENT`. El canal `EMAIL` usa `SimulatedDeliveryProvider`, que sólo escribe al log y luego marca `SENT`. No hay dirección de contacto.

Catalog conserva `events.starts_at` y `event_snapshots.starts_at`; búsqueda, detalle y recomendaciones leen la fecha del snapshot. Event Management sólo edita borradores y publica avisos de ciclo de vida con `EventNotificationPublisher` después del commit. El publicador vive en memoria y sus hechos pueden perderse si Notification Service no responde o el productor reinicia. Purchase usa el detalle público de Catalog para la ventana de devolución de 72 horas.

## Goals / Non-Goals

**Goals:** entregar el canal `EMAIL` existente a un SMTP local verificable; permitir postergar la fecha/hora de un evento publicado bajo las reglas acordadas; avisar a sus compradores mediante el pipeline F11 actual; mantener consistentes las lecturas públicas de fecha y la regla de devolución.

**Non-Goals:** cambiar el canal `PUSH` simulado, construir una interfaz, añadir un outbox en productores, rehacer la resolución de fans/compradores, actualizar la fecha histórica en órdenes/tickets o QR, garantizar recepción en una casilla externa o envío SMTP exactamente una vez.

## Decisions

### D1 — Contacto y envío real del canal EMAIL

Notification Service agrega `customer_email_contacts` con `customer_id` único y email validado. `PUT/GET /api/notifications/me/email` usan sólo el `X-User-Id` derivado del JWT por Gateway; el cuerpo de PUT contiene únicamente `email`. La ruta existente `/api/notifications/**` y su regla `CLIENT` ya cubren el endpoint. El registro de email no depende de un proveedor de login nuevo.

La entrega selecciona por canal: `EMAIL` consulta el contacto y usa SMTP configurable; `PUSH` conserva la simulación. Si falta contacto, el mensaje `EMAIL` pasa a `SKIPPED_NO_CONTACT` sin enviar. Si SMTP falla, se conserva el mecanismo actual de reintento y `FAILED`; `SENT` se asigna sólo cuando el servidor acepta el correo. El cuerpo almacenado de la notificación es el cuerpo enviado; no contiene QR ni datos de pago. Contactos registrados después de `SKIPPED_NO_CONTACT` no reabren avisos históricos.

Mailpit se añade al Compose existente como SMTP interno y UI/API de inspección en `127.0.0.1:8025`. Esta es la forma mínima de comprobar una entrega real por protocolo sin depender de una cuenta externa. La API de Mailpit comprueba destinatario, asunto y texto. El canal `PUSH` y la bandeja siguen funcionando aunque SMTP esté caído.

### D2 — Postergación segura sobre la fecha vigente

`events.starts_at` pasa a representar la fecha vigente; `event_snapshots.starts_at` conserva la fecha original. Se agregan `schedule_revision` (inicial 0) y un historial con evento, clave de idempotencia, actor, fecha anterior/nueva, revisión y `change_id` estable. La operación `PATCH /api/management/events/{id}/schedule` recibe `startsAt`, `expectedScheduleRevision` e `Idempotency-Key`. Un bloqueo de fila o actualización condicional asegura que dos solicitudes sobre la misma revisión no confirmen ambas. Un replay idéntico devuelve el resultado persistido; la misma clave con otro cuerpo da `409`.

Catalog comprueba `PUBLISHED`, dueño `ORGANIZER` o `ADMIN`, evento aún no iniciado, y que la fecha nueva sea estrictamente posterior a la vigente y a `Clock.instant() + 72h`. Compara instantes, no textos de fecha. No modifica lugar, participantes, sectores, precios, capacidad ni snapshot. Se inyecta `Clock` para probar el borde exacto de 72 horas.

### D3 — Fecha visible y aviso de postergación

Búsqueda, detalle y candidatos de recomendaciones leen `events.starts_at` para seleccionar, filtrar, ordenar y mostrar la fecha vigente; el resto de los datos sigue en el snapshot. La caché de búsqueda y detalle se invalida después del commit. Purchase conserva `RefundPolicy`: su lectura del detalle público recibirá la nueva fecha. Las órdenes y tickets mantienen la fecha capturada al comprar y el QR no cambia; el aviso recibido comunica la fecha nueva al comprador.

Catalog emite `EVENT_POSTPONED` mediante el publicador `afterCommit` existente, con `change_id` del historial y fechas anterior/nueva. Notification amplía el tipo admitido, la plantilla y la elección de destinatarios para reutilizar `buyersOf(eventId)`. La unicidad actual del disparador y de mensaje por destinatario/canal deduplica el replay. Un cambio rechazado no publica nada.

## Risks / Trade-offs

| Riesgo | Tratamiento |
|---|---|
| Notification Service está caído cuando el productor publica | El publicador actual reintenta acotadamente y deja log local; no recupera automáticamente tras reinicio. Se documenta como límite existente y no se afirma entrega garantizada. |
| SMTP acepta un mensaje pero se pierde la respuesta | Puede repetirse el correo; la base mantiene un único registro lógico, pero no existe transacción compartida con SMTP. |
| Un comprador consulta una orden antigua | `eventStartsAt` sigue siendo la fecha de compra. El aviso y el catálogo muestran la vigente; actualizar la vista de órdenes queda fuera de este cambio mínimo. |
| Un cliente registra contacto después de un aviso omitido | `SKIPPED_NO_CONTACT` permanece histórico. El siguiente aviso ya usa el nuevo contacto. |
| Mailpit se confunde con correo externo | Se documenta que Mailpit acepta SMTP y captura mensajes localmente; `SENT` no prueba recepción en una casilla externa. |

## Migration Plan

1. Agregar tablas/columnas compatibles con datos existentes: contactos, revisión 0 e historial de programación. Ampliar los checks de tipos/estados en Notification sin perder filas.
2. Añadir Mailpit y configuración SMTP; conservar el proveedor `PUSH` simulado. Los contactos existentes se inician vacíos.
3. Habilitar la operación de postergación y `EVENT_POSTPONED` cuando el consumidor acepte el nuevo tipo. Verificar Gateway, PostgreSQL, SMTP/Mailpit y flujo Postman con datos nuevos.
4. En rollback, retirar el endpoint y el envío SMTP nuevo, conservando contactos e historial. Una fecha ya postergada no se revierte automáticamente.
