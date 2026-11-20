# Spec Delta

## MODIFIED Requirements

### Requirement: Buscar sólo eventos visibles

El sistema SHALL exponer públicamente en resultados de búsqueda únicamente eventos cuyo estado actual sea `PUBLISHED`. El mismo criterio SHALL regir el contrato interno de candidatos para recomendaciones, que sólo SHALL incluir eventos `PUBLISHED` con `startsAt` futuro, acotados a 200 por proximidad. Trazabilidad: PDF-F10 y PDF-NF-CONSISTENCY.

#### Scenario: Buscar con eventos en estados diferentes

- **GIVEN** existen eventos `DRAFT`, `PUBLISHED` y `SUSPENDED`
- **WHEN** un cliente realiza una búsqueda pública
- **THEN** el sistema devuelve sólo los eventos `PUBLISHED`

#### Scenario: Evento suspendido deja de ser visible

- **GIVEN** un evento aparecía en resultados públicos
- **WHEN** un administrador lo suspende
- **THEN** las búsquedas públicas posteriores dejan de incluirlo

#### Scenario: Candidatos de recomendación sólo publicados

- **GIVEN** eventos en estado `DRAFT`, `PUBLISHED`, `SUSPENDED` y `CANCELLED`
- **WHEN** Preferences solicita `GET /internal/events/recommendation-candidates`
- **THEN** Catalog devuelve únicamente los eventos `PUBLISHED`

#### Scenario: Candidatos con fecha futura

- **GIVEN** eventos publicados con `startsAt` anterior y posterior al instante de la consulta
- **WHEN** Preferences solicita los candidatos
- **THEN** Catalog excluye los eventos cuya fecha ya pasó

#### Scenario: Candidatos acotados por proximidad

- **GIVEN** más de 200 eventos publicados y futuros
- **WHEN** Preferences solicita los candidatos con `limit=200`
- **THEN** Catalog devuelve los 200 más próximos por `startsAt` ascendente y `eventId` ascendente
