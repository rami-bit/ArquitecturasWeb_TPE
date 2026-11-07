# Spec Delta

## ADDED Requirements

### Requirement: Exponer el snapshot publicado para inicializar inventario

Catalog SHALL exponer un contrato interno de lectura que devuelva el snapshot publicado de un evento con los identificadores de origen de sus sectores y asientos, sus tipos, capacidades y datos de ubicación. El contrato SHALL NOT incluir precios y SHALL quedar disponible sólo para otros servicios de la red interna.

#### Scenario: Consultar el snapshot de un evento publicado

- **GIVEN** un evento publicado con snapshot y sectores numerados y de admisión general
- **WHEN** Inventory solicita el snapshot por el contrato interno
- **THEN** Catalog devuelve el identificador del evento, el del snapshot, los datos del lugar y cada sector con su identificador de origen, tipo y capacidad, incluyendo los asientos con su identificador de origen, fila y número

#### Scenario: Consultar un evento sin snapshot

- **GIVEN** un evento inexistente, en estado `DRAFT` o sin snapshot
- **WHEN** Inventory solicita su snapshot por el contrato interno
- **THEN** Catalog responde `404` sin revelar datos del evento

#### Scenario: El snapshot consultado es inmutable

- **GIVEN** un evento publicado con snapshot
- **WHEN** cambia la plantilla canónica del lugar o sus sectores
- **THEN** el contrato interno sigue devolviendo el snapshot original sin modificaciones

#### Scenario: No exponer precios en el contrato interno

- **GIVEN** un snapshot publicado que contiene precios por sector
- **WHEN** Inventory consulta el snapshot
- **THEN** la respuesta no incluye precios ni ofertas económicas

#### Scenario: El contrato interno no se publica por el Gateway

- **WHEN** un cliente externo intenta acceder al contrato interno de snapshot a través del Gateway
- **THEN** el Gateway no lo enruta y responde como ruta no encontrada
