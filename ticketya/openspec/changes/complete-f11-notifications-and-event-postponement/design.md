# Design

## Context

Ver `proposal.md` para la motivación y `specs/` para el comportamiento exigido. Catalog conserva `events.starts_at` y `event_snapshots.starts_at`; las lecturas públicas mezclan ambas fuentes. Purchase y Ticket guardan la fecha recibida al emitir una entrada. Purchase usa la fecha pública actual de Catalog al comprobar el límite de devolución de 72 horas. Preferences conoce ciudad y artistas favoritos de cada cliente, pero ningún servicio conoce su email. El JWT sólo garantiza `sub` UUID y rol.

`NotificationDispatcher` de Purchase registra `ORDER_CONFIRMED` al confirmar una compra por la ruta inicial; la reconciliación también puede pasar una orden a `CONFIRMED` y hoy no lo publica. `ORDER_CANCELLED` representa una compensación de orden, no una cancelación de evento. Catalog no emite avisos. Los envíos actuales son volátiles: un log local no permite recuperar automáticamente un evento tras reiniciar el productor.

## Goals / Non-Goals

**Goals:** preservar las invariantes de venta y QR; permitir sólo postergaciones válidas y autorizadas; entregar emails verificables sin bloquear operaciones de negocio; conservar trabajo pendiente y deduplicar eventos; mantener una ruta de email propia protegida y un Compose reproducible.

**Non-Goals:** adelantar eventos, editar lugar/asientos/precios/participantes publicados, crear una excepción de devolución por postergación, enviar push, operar un proveedor de email real en Compose, garantizar recepción en una casilla externa o garantizar exactamente una transmisión SMTP ante una respuesta ambigua.

## Decisions

### D1 — Fecha vigente separada del snapshot publicado

`events.starts_at` será la fecha vigente y `event_snapshots.starts_at` seguirá siendo la fecha original de publicación. Se añadirá `events.schedule_revision` (entero desde 0) y una tabla de historial de postergaciones con fecha previa/nueva, actor, instante y correlation ID. Catalog hará el cambio bajo bloqueo de fila o actualización condicional por revisión, con `expectedScheduleRevision` e `Idempotency-Key` del request; dos cambios concurrentes no podrán confirmar sobre la misma revisión. `PATCH /api/management/events/{id}/schedule` admitirá sólo `PUBLISHED`, actor dueño o ADMIN, fecha nueva estrictamente posterior a la vigente y a `Clock.instant() + 72h`. Se compararán instantes, no textos ni horarios locales. Un evento ya iniciado no se reprogramará. La respuesta incluirá revisión y fechas anterior/nueva. Un replay de la misma clave y cuerpo devolverá la respuesta inicial; reutilizar la clave con otro cuerpo dará `409`.

Se descarta modificar el snapshot: Inventory y la emisión idempotente dependen de una configuración estable. En las consultas públicas e internas se superpone `events.starts_at` sobre el snapshot para la fecha vigente; nombre, lugar, participantes, sectores y precios siguen viniendo del snapshot. Se actualizarán los dos usos de fecha en búsqueda (`SELECT` y filtros/orden), los candidatos de recomendaciones y los contratos internos de compra/inventario. La caché se invalida después del commit.

### D2 — Fecha original y fecha actual en la orden

`purchase_orders.event_starts_at` y `tickets.event_starts_at` siguen siendo el dato capturado al emitir. La vista de orden agrega `currentEventStartsAt`, resuelto desde un nuevo `GET /internal/events/{eventId}/schedule` de Catalog que devuelve fecha, revisión y estado incluso si el evento está cancelado; la API pública de detalle sólo muestra `PUBLISHED` y no sirve para este caso. No se cambia `eventStartsAt` ni el QR. Si Catalog no responde, Purchase devuelve la orden y el QR con `currentEventStartsAt: null` y una advertencia; nunca presenta el dato histórico como fecha actual. Para listas se agruparán consultas por evento a fin de evitar una llamada por ticket. El control de devolución sigue leyendo la fecha vigente desde Catalog, sin modificar `RefundPolicy`.

Se descarta reescribir todas las órdenes y tickets cada vez que cambia una fecha: requeriría una actualización distribuida y podría dejar réplicas parcialmente actualizadas. El emisor de tickets mantiene el snapshot histórico; la consulta de la orden es el punto público de información vigente para el titular.

### D3 — Servicio de notificaciones dueño del contacto y de la entrega

`notification-service` será un módulo Spring Boot con PostgreSQL propio. `PUT/GET /api/notifications/me/email` permitirá a `CLIENT` registrar/consultar una dirección sintácticamente válida; el servicio deriva `customerId` del encabezado interno generado por el Gateway y no acepta identificadores en el cuerpo. La tabla de contactos tiene `customer_id` único. Un `POST /internal/notifications` recibe un evento de negocio con `eventId` estable, tipo, `sourceId`, `correlationId` y los IDs/datos mínimos necesarios. `sourceId` será `orderId` para compra, `eventId` para publicación/cancelación y `eventId:revision` para postergación. La tabla de eventos deduplica `eventId`; la tabla de mensajes deduplica `(tipo, sourceId, customerId)` y conserva estado, intentos, error y fecha SMTP. Los contratos internos quedan fuera del Gateway.

Mailpit es un SMTP de prueba en Compose, con interfaz/API ligada sólo a `127.0.0.1:8025`; el puerto SMTP es interno. `JavaMailSender` usa host/puerto configurables. `SENT` significa que SMTP aceptó el mensaje, verificable mediante la API de Mailpit, no que una casilla externa lo recibió. Se descarta leer emails del JWT: su contrato actual no incluye ese claim y los productores no deben confiar en un dato no validado.

### D4 — Productores durables y procesamiento idempotente

Catalog guardará en su propia base un evento outbox en la misma transacción que la primera publicación, la primera transición a `CANCELLED` y cada postergación confirmada. Purchase guardará un evento outbox junto con la transición a `CONFIRMED`, tanto en creación como en reconciliación. Los workers enviarán por REST a Notification Service fuera de la solicitud HTTP, con timeout y reintentos acotados; el `eventId` de outbox se mantiene al reintentar. El dispatcher existente conserva log local y circuit breaker, pero el outbox será la fuente de recuperación. El evento `ORDER_CANCELLED` de compensación queda separado y no dispara el aviso de cancelación de evento.

Notification procesa eventos persistidos en páginas reanudables. Compra usa el `customerId` del evento. Para cancelación y postergación consulta a Purchase un contrato interno paginado de compradores confirmados distintos por evento; una compra con varias entradas produce un destinatario. Para publicación consulta a Preferences un contrato interno paginado de audiencia por ciudad y participantes, con coincidencia normalizada. Preferences mantendrá campos normalizados consultables e índices en su propio documento MongoDB; se actualizarán los documentos existentes sin perder preferencias. El destinatario se determina con los datos disponibles al procesar cada página; esto limita la precisión si cambian preferencias/contactos mientras un evento espera por una dependencia. Se documentará esa consistencia eventual. Una fila por destinatario permite reanudar después de un fallo sin repetir envíos marcados `SENT`.

Los fallos SMTP no cambian la respuesta de la operación de negocio. Los mensajes fallidos se reintentan hasta el límite configurado; el estado final conserva causa y correlación. Ante respuesta SMTP ambigua puede llegar un correo duplicado aunque la base tenga una sola fila: SMTP no ofrece una transacción compartida con PostgreSQL. Se descarta una llamada síncrona directa desde Purchase/Catalog porque perdería avisos ante una caída del receptor o bloquearía operaciones críticas.

### D5 — Contratos, seguridad y verificación

El Gateway enruta `/api/notifications/**` antes de `/api/**` y exige `CLIENT`; la ruta de programación acepta `ORGANIZER` o `ADMIN`, mientras Catalog comprueba ownership. Se actualizan OpenAPI consolidado y de servicios, Postman, README, diagramas y documentación de diseño. Las pruebas de servicio cubrirán PostgreSQL real para outbox/índices de unicidad, MongoDB real para audiencia, Mailpit para aceptación y contenido, y casos de error/rol por Gateway. El E2E verificará compra, cancelación, postergación y publicación coincidente, además de reintentos y SMTP caído. Los casos de tiempo usarán reloj inyectado: igualdad exacta a 72 horas falla; un instante posterior pasa sólo si también posterga la fecha vigente.

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| Confirmación por reconciliación sin aviso | Centralizar transición `CONFIRMED` y escritura outbox en el repositorio; probar ambas rutas. |
| Publicación o cancelación repetida genera avisos nuevos | Crear evento outbox sólo en la primera transición; usar claves estables y unicidad en Notification. |
| Fallo entre cambio de fecha y publicación del aviso | Guardar historial, revisión y outbox en la misma transacción de Catalog. |
| Vistas con fecha histórica confundida con vigente | Campo nuevo `currentEventStartsAt` y advertencia explícita cuando Catalog no responde. |
| Correo repetido tras aceptación SMTP sin respuesta | Conservar una fila por destinatario y documentar el límite de entrega exactamente una vez. |
| Fan-out grande o caída de Purchase/Preferences | Páginas reanudables, estado persistido, timeouts y reintentos; no bloquear la solicitud original. |
| Mailpit se confunde con envío real | Documentar que sólo captura correo local y que la API de TicketYa sigue entrando por Gateway. |

## Migration Plan

1. Agregar columnas/tablas compatibles con registros existentes: revisión `0`, historial/outbox en Catalog, outbox en Purchase y esquema propio de Notification. Las órdenes y tickets existentes conservan su fecha histórica.
2. Incorporar Notification Service y Mailpit al Compose con variables seguras; configurar primero el receptor, luego habilitar productores. Los eventos previos al despliegue no se reconstruyen automáticamente.
3. Habilitar la ruta de email y programación, verificar healthchecks y ejecutar el E2E por Gateway y la API de Mailpit. Una operación sin email registrado queda `SKIPPED_NO_CONTACT`.
4. Para rollback, deshabilitar publicación/consumo de outbox y retirar rutas nuevas; conservar datos de revisión y programación ya confirmados. No revertir fechas de eventos postergados ni borrar mensajes sin una migración explícita.
