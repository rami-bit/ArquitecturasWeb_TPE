# Tasks

## 1. Notification Service

- [x] 1.1 Crear `services/notification-service` (Spring Boot, JDBC, PostgreSQL) con `notification_triggers` y `notifications`, restricciones únicas e índices de pendientes.
- [x] 1.2 Ingesta `POST /internal/notifications` y `POST /internal/notifications/event-changes` con validación por tipo y deduplicación; probar `202` y `duplicate`.
- [x] 1.3 `TriggerProcessor` y `DeliveryWorker` con arrendamiento, `SKIP LOCKED`, reintentos exponenciales y `FAILED`; probar dependencia caída, pasarela caída, recuperación y procesamiento concurrente sin duplicados (`NotificationServiceContractTest`).
- [x] 1.4 Bandeja `GET /api/notifications/me` sólo para `CLIENT`; probar roles, identidad y paginación.

## 2. Productores y destinatarios

- [x] 2.1 Purchase: datos del evento en el hecho, avisos al reconciliar y `GET /internal/events/{eventId}/buyers` (`EventBuyersIntegrationTest`, `NotificationDispatcherPayloadTest`).
- [x] 2.2 Catalog: `EventNotificationPublisher` y envío `afterCommit` en publicar, suspender, reanudar y cancelar (`EventChangeNotificationIntegrationTest`, `EventNotificationPublisherTest`).
- [x] 2.3 Preferences: `GET /internal/preferences/matches` sobre MongoDB (`PreferenceMatchesMongoIntegrationTest`).

## 3. Gateway, contratos y despliegue

- [x] 3.1 Ruta `/api/notifications/**` sólo `CLIENT` y su prueba de roles.
- [x] 3.2 OpenAPI (5 operaciones, 7 esquemas) y test contractual; Compose con `notification-db` y `notification-service`.
- [x] 3.3 Postman `F11-01`…`F11-12` y verificación por el Gateway.
