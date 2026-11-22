# Proposal

## Why

PDF-F11 pide notificaciones por email o push en tres momentos: al confirmar una compra, ante la cancelación o el cambio de un evento, y cuando un artista favorito de un cliente publica un evento en su ciudad. Hasta ahora Purchase sólo tenía un despacho con fallback local (`NotificationDispatcher`) sin servicio receptor. Catalog no informaba los cambios de sus eventos y no existía cómo encontrar a los fans de un artista.

## What Changes

- Agregar `notification-service` (Spring Boot + PostgreSQL propio) con ingesta interna idempotente, resolución de destinatarios, entrega por email y push simulados con reintentos, y bandeja `GET /api/notifications/me` para `CLIENT`.
- Purchase envía también los datos públicos del evento, notifica las órdenes confirmadas o compensadas al reconciliar y expone `GET /internal/events/{eventId}/buyers`.
- Catalog publica después del commit la publicación, suspensión, reanudación y cancelación de cada evento.
- Preferences expone `GET /internal/preferences/matches` (MongoDB).
- Gateway: ruta `/api/notifications/**` sólo para `CLIENT`.
- Compose (19 contenedores), OpenAPI, Postman y documentación.

## Capabilities

### New Capabilities

- `customer-notifications`: notificaciones al cliente por compra, cambios de evento y artistas favoritos.

### Modified Capabilities

- `api-gateway`: enrutar y proteger la bandeja de notificaciones.

## Impact

- **Trazabilidad:** PDF-F11, PDF-NF-RESILIENCE, PDF-NF-ASYNC, PDF-NF-SECURITY.
- **Componentes:** `notification-service` y `notification-db` nuevos; cambios acotados en Purchase, Catalog, Preferences y Gateway.
- **Fuera de alcance:** proveedor real de email/push (la pasarela es simulada, como Payment Mock), preferencias de canal por cliente y plantillas configurables.
