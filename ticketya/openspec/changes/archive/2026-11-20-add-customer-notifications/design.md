# Design

## Context

Purchase ya publicaba `ORDER_CONFIRMED` y `ORDER_CANCELLED` con `NotificationDispatcher` (asíncrono, circuit breaker y fallback local) hacia `POST /internal/notifications`, pero nadie atendía ese contrato. Preferences guarda en MongoDB la ciudad y los artistas favoritos de cada cliente. Catalog es dueño del ciclo de vida del evento.

## Decisions

### D1. Servicio propio con PostgreSQL

Las notificaciones tienen estado (pendiente, enviada, fallida), reintentos y consultas por cliente; son datos transaccionales y relacionales. PostgreSQL (como Audit) da restricciones únicas y `FOR UPDATE SKIP LOCKED`. La base NoSQL del TP sigue siendo la de Preferences.

### D2. Inbox y outbox en lugar de un broker

`notification_triggers` (inbox) guarda cada hecho con una `dedupe_key` única; `notifications` (outbox) guarda una fila por destinatario y canal. Un proceso programado reclama filas con arrendamiento y `SKIP LOCKED`. Así se logra la entrega asíncrona, durable y multi-instancia sin agregar Kafka o RabbitMQ al Compose.

### D3. Claves de deduplicación

`tipo:orderId` para órdenes y `tipo:eventId` para publicación y cancelación, que ocurren una sola vez; `tipo:eventId:changeId` para suspensión y reanudación, que pueden repetirse. Catalog genera un `changeId` por transición.

### D4. Destinatarios por contratos internos

Purchase devuelve los clientes distintos con órdenes `CONFIRMED` o `PAID_PENDING_TICKETS`. Preferences devuelve los clientes cuya ciudad coincide y que tienen un artista del evento como favorito; la comparación es exacta e insensible a mayúsculas, con `Pattern.quote` para no interpretar el nombre como expresión regular. Ningún servicio lee bases ajenas.

### D5. Catalog notifica después del commit

El cambio se arma dentro de la transacción, cuando el lugar y los participantes están cargados, y se envía en `afterCommit`, el mismo patrón que la invalidación de cache. Una transición rechazada o revertida no notifica.

### D6. Resiliencia

Los productores usan envío fire-and-forget con timeouts y reintentos acotados; Purchase mantiene su circuit breaker. En el servicio, la resolución de destinatarios y cada entrega se reintentan con espera exponencial (base 2 s, tope 60 s, 6 intentos) y luego quedan `FAILED` con el último error.

### D7. Pasarela simulada

`SimulatedDeliveryProvider` registra cada email o push en el log `ticketya.delivery`, de forma análoga a Payment Mock. Con `NOTIFICATION_PROVIDER_MODE=FAILING` simula una caída. La identidad es externa (IdP), así que la dirección de email o el dispositivo los resuelve el proveedor real a partir del `customerId`.

## Risks / Trade-offs

- El procesamiento es casi inmediato (cada 1 s), no en tiempo real.
- Si Catalog cae entre el commit y el envío, el cambio queda sólo en su log local (la misma garantía que la bitácora). Un outbox en Catalog sería la evolución.
