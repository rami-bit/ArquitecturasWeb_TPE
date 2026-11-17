# Spec Delta

## ADDED Requirements

### Requirement: Enrutar y proteger el control de acceso

El Gateway SHALL enrutar `/api/access/**` a Ticket Service. SHALL exigir el rol `ACCESS_STAFF` para `POST /api/access/events/{eventId}/validations` y `ACCESS_STAFF` o `ADMIN` para `GET /api/access/events/{eventId}/scans`, y responder `403` problem+json sin invocar el servicio a cualquier otro rol. Trazabilidad: PDF-NF-GATEWAY y PDF-NF-SECURITY.

#### Scenario: Personal de acceso valida

- **WHEN** `ACCESS_STAFF` envía una validación por el Gateway
- **THEN** Ticket Service recibe la misma ruta con la identidad derivada

#### Scenario: Cliente intenta validar

- **WHEN** un `CLIENT` envía una validación
- **THEN** el Gateway responde `403` sin invocar Ticket Service
