# Spec Delta

## ADDED Requirements

### Requirement: Enrutar y proteger los reportes del organizador

El Gateway SHALL enrutar `/api/reports/**` a Purchase Service, exigir un JWT con rol `ORGANIZER` o `ADMIN` y responder `403` problem+json sin invocar el servicio a cualquier otro rol. Trazabilidad: PDF-NF-GATEWAY y PDF-NF-SECURITY.

#### Scenario: Organizador consulta

- **WHEN** un `ORGANIZER` solicita `GET /api/reports/events/{eventId}` por el Gateway
- **THEN** Purchase recibe la misma ruta con la identidad derivada

#### Scenario: Cliente consulta

- **WHEN** un `CLIENT` solicita un reporte
- **THEN** el Gateway responde `403` sin invocar Purchase
