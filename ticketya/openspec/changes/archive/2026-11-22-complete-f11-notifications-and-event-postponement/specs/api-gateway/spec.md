# Spec Delta

## ADDED Requirements

### Requirement: Proteger la postergación de un evento

El Gateway SHALL permitir `PATCH /api/management/events/{id}/schedule` sólo a `ORGANIZER` o `ADMIN`, propagando la identidad validada a Catalog, donde se comprueba ownership. SHALL preservar `Idempotency-Key` y el cuerpo. Trazabilidad: PDF-F11 y PDF-NF-SECURITY.

#### Scenario: Actor permitido

- **WHEN** un `ORGANIZER` o `ADMIN` solicita una postergación con JWT válido
- **THEN** Catalog recibe el request con identidad validada y clave de idempotencia

#### Scenario: Cliente o usuario anónimo

- **WHEN** un `CLIENT` o un usuario sin JWT solicita postergar
- **THEN** el Gateway responde `403` o `401` sin invocar Catalog
