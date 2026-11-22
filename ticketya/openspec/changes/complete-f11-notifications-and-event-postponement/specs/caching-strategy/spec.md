# Spec Delta

## ADDED Requirements

### Requirement: Invalidar el catálogo al postergar un evento

Catalog SHALL invalidar búsqueda y detalle cacheados después del commit de una postergación confirmada. El TTL existente SHALL seguir limitando la obsolescencia ante fallo de invalidación. Trazabilidad: PDF-F11 y PDF-NF-CACHE.

#### Scenario: Postergación confirmada

- **WHEN** se confirma una postergación con búsqueda y detalle cacheados
- **THEN** las lecturas siguientes muestran la fecha vigente y el orden actualizado

#### Scenario: Postergación rechazada

- **WHEN** se rechaza una postergación
- **THEN** no se invalida la caché por ese intento ni se publica aviso
