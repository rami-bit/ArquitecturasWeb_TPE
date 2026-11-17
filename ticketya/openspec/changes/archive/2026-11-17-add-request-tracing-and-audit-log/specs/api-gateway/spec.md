# Spec Delta

## ADDED Requirements

### Requirement: Enrutar y proteger la bitácora centralizada

El Gateway SHALL enrutar `/api/audit/**` a Audit Service sin reescribir el path, exigir un JWT válido con rol `ADMIN` antes de invocarlo y responder `403` en `application/problem+json` con `X-Correlation-Id` a cualquier otro rol. `/internal/audit-events` SHALL permanecer sin ruta pública. Trazabilidad: PDF-NF-GATEWAY, PDF-NF-SECURITY y PDF-NF-TRACE.

#### Scenario: ADMIN consulta la bitácora

- **WHEN** un `ADMIN` solicita `GET /api/audit/traces/{correlationId}` por el Gateway
- **THEN** Audit Service recibe la misma ruta con la identidad derivada

#### Scenario: Otro rol consulta la bitácora

- **WHEN** un `CLIENT` solicita `GET /api/audit/events`
- **THEN** el Gateway responde `403` sin invocar Audit Service
