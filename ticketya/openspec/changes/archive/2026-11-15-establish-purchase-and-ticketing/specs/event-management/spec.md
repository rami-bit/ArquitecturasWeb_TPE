# Spec Delta

## ADDED Requirements

### Requirement: Exponer un snapshot de compra con precios

Catalog SHALL exponer un contrato interno de lectura, separado del snapshot de inventario, que devuelva el snapshot publicado con los datos del evento, lugar, sectores, asientos y el precio de cada sector. El contrato SHALL NOT publicarse por el Gateway.

#### Scenario: Consultar el snapshot de compra de un evento publicado

- **GIVEN** un evento publicado con snapshot y sectores numerados y de admisión general
- **WHEN** Purchase solicita el snapshot de compra por el contrato interno
- **THEN** Catalog devuelve el evento, el lugar, cada sector con su identificador de origen, tipo, capacidad y precio, y los asientos con su identificador de origen, fila y número

#### Scenario: Evento no comprable

- **GIVEN** un evento inexistente, en borrador, suspendido o cancelado
- **WHEN** Purchase solicita el snapshot de compra
- **THEN** Catalog responde `404` sin revelar datos del evento

#### Scenario: El snapshot de compra es inmutable

- **GIVEN** un evento publicado con snapshot
- **WHEN** cambia la plantilla canónica del lugar o sus sectores
- **THEN** el contrato interno sigue devolviendo el snapshot original con los precios publicados

#### Scenario: El snapshot de inventario no cambia

- **GIVEN** el contrato interno de snapshot consumido por Inventory
- **WHEN** se agrega el snapshot de compra
- **THEN** el contrato de Inventory sigue sin incluir precios y conserva su respuesta

#### Scenario: El contrato de compra no se publica por el Gateway

- **WHEN** un cliente externo intenta acceder al snapshot de compra a través del Gateway
- **THEN** el Gateway no lo enruta y responde como ruta no encontrada
