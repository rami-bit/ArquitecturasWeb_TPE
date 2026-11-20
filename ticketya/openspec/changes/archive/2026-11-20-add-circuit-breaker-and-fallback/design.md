# Design

## Context

`PurchaseService` orquesta la saga de compra con clientes síncronos hacia Catalog, Inventory, Payment, Ticket y Queue. `ServiceClient` ya aplica timeouts de 5 s/10 s y traduce fallos a `ServiceException`, pero no corta el flujo cuando una dependencia falla repetidamente. `AuditPublisher` muestra el patrón de publicación fire-and-forget con fallback local que se reutiliza para notificaciones.

## Goals / Non-Goals

**Goals:** fallar rápido sin llamar a una dependencia caída; no confundir rechazos de negocio con fallas de infraestructura; recuperarse automáticamente con una llamada de prueba; conservar la consistencia de la saga (sin cobros duplicados ni reservas huérfanas); dejar el evento de notificación en el log local aunque el adaptador remoto falle.

**Non-Goals:** agentes externos, métricas micrometer, persistencia del estado del circuito, implementar F11 y modificar contratos HTTP.

## Decisions

### D1 — Componente propio y sin dependencias nuevas

`CircuitBreaker` con estados `CLOSED`, `OPEN` y `HALF_OPEN`, `synchronized`, umbral de fallas consecutivas, duración de apertura y una llamada de prueba en semiabierto. Se descartó Resilience4j por ser una dependencia nueva sin beneficio para el volumen didáctico.

### D2 — Sólo fallas de infraestructura abren el circuito

En `PaymentClient`, cuentan como falla los `ServiceException` sin status (timeout/conexión) y los `5xx`. Un `402 REJECTED` o un `404` son respuestas de negocio: no abren el circuito y además reinician el contador de fallas porque el servicio respondió.

### D3 — Circuito abierto = fallo rápido sin efecto secundario

`charge` lanza `ServiceException(503, "circuit-breaker-open")` antes de construir la request. Purchase libera la reserva y responde `503` con `application/problem+json`, sin marcar la orden como pagada ni dejar cupos retenidos.

### D4 — Recuperación en semiabierto

Vencida la duración de apertura, se permite una única llamada de prueba. Éxito cierra el circuito y reinicia contadores; falla lo reabre. Nunca hay más de una llamada de prueba simultánea.

### D5 — Notificaciones con fallback local

`NotificationDispatcher` escribe siempre el evento en el log local `ticketya.notification` con `correlationId`, `orderId`, `customerId` y tipo. Si `NOTIFICATION_SERVICE_URL` está vacía, no hay envío remoto. Si está configurada, el envío es asíncrono y usa el mismo circuito; ante falla o circuito abierto, el evento queda sólo en el log local y la compra continúa. El dispatcher nunca lanza excepciones al llamador.

### D6 — Eventos publicados

Sólo `ORDER_CONFIRMED` al confirmar la orden y `ORDER_CANCELLED` al compensarla. No se publican datos de pago ni payloads QR.

### D7 — Configuración y observabilidad

Umbral y duración por variables de entorno con valores por omisión 5 y 30 s. Cada cambio de estado y cada evento descartado se registran con SLF4J; los contadores del circuito se exponen como getters para pruebas.

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| El circuito se abre por una racha de rechazos de pago | D2 distingue `4xx` de fallas de infraestructura |
| Una llamada de prueba en semiabierto coincide con varias compras concurrentes | D4 limita a una llamada de prueba; las demás fallan rápido con `503` |
| Estado del circuito no compartido entre réplicas | Simplificación declarada; no hay múltiples réplicas en el Compose didáctico |
| Notificaciones sin entrega real | F11 queda pendiente; el fallback local conserva la traza y no bloquea la compra |
