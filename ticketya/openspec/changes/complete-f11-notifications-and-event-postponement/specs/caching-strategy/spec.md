# Spec Delta

## ADDED Requirements

### Requirement: Invalidar el catálogo al postergar un evento

Catalog SHALL invalidar las respuestas cacheadas de búsqueda y detalle tras confirmar una postergación, de modo que las lecturas siguientes usen la fecha vigente. La invalidación SHALL ocurrir después del commit y SHALL conservar el TTL como límite de obsolescencia ante fallo. Trazabilidad: PDF-F11 y PDF-NF-CACHE.

#### Scenario: Búsqueda y detalle estaban cacheados

- **GIVEN** un evento aparece en una búsqueda y detalle cacheados
- **WHEN** se confirma su postergación
- **THEN** las siguientes lecturas muestran la fecha vigente y el orden cronológico actualizado

#### Scenario: Postergación rechazada

- **WHEN** una postergación falla por permisos, estado o fecha inválida
- **THEN** no se invalida una fecha que no cambió ni se publica un aviso
