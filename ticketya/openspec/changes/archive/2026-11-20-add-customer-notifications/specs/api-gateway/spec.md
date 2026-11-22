# Spec Delta

## ADDED Requirements

### Requirement: Enrutar y proteger la bandeja de notificaciones

El Gateway SHALL enrutar `/api/notifications/**` a Notification Service antes del comodín de Catalog, exigir un JWT con rol `CLIENT` y responder `403` problem+json sin invocar el servicio a cualquier otro rol. Los contratos `/internal/notifications/**`, `/internal/events/{eventId}/buyers` y `/internal/preferences/matches` SHALL permanecer sin ruta pública. Trazabilidad: PDF-NF-GATEWAY y PDF-NF-SECURITY.

#### Scenario: Cliente consulta su bandeja

- **WHEN** un `CLIENT` solicita `GET /api/notifications/me` por el Gateway
- **THEN** Notification Service recibe la misma ruta con la identidad derivada

#### Scenario: Otro rol consulta la bandeja

- **WHEN** un `ORGANIZER` solicita `GET /api/notifications/me`
- **THEN** el Gateway responde `403` sin invocar Notification Service
