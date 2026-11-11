# Spec Delta

## ADDED Requirements

### Requirement: Exponer los datos de origen y presentación de una reserva

Inventory SHALL incluir en cada ítem de la respuesta de reserva el identificador de origen del sector y del asiento de Catalog, el nombre y tipo de sector y, cuando corresponda, la fila y el número de asiento. Estos datos SHALL permitir a Purchase valorizar la orden y emitir tickets sin acceder a `inventory_db`.

#### Scenario: Reserva de asientos numerados

- **GIVEN** una reserva creada para asientos de un sector numerado
- **WHEN** Inventory devuelve la reserva
- **THEN** cada ítem incluye el identificador de origen del sector y del asiento, el nombre y tipo de sector, la fila y el número de asiento

#### Scenario: Reserva de admisión general

- **GIVEN** una reserva creada para cupos de un sector de admisión general
- **WHEN** Inventory devuelve la reserva
- **THEN** cada ítem incluye el identificador de origen del sector, el nombre y tipo de sector, la cantidad reservada y no incluye asiento, fila ni número

#### Scenario: Identificadores de Inventory preservados

- **GIVEN** una reserva con ítems enriquecidos
- **WHEN** Purchase confirma o libera la reserva
- **THEN** Inventory sigue aceptando los identificadores de sector y asiento de Inventory usados en la solicitud

#### Scenario: La reserva no expone precios ni comprador

- **GIVEN** una reserva devuelta por Inventory
- **WHEN** se inspecciona la respuesta
- **THEN** no incluye precios, datos del comprador, tokens ni identificadores de pago

#### Scenario: Repetición idempotente conserva los datos

- **GIVEN** una reserva creada con sus ítems enriquecidos
- **WHEN** Purchase repite la misma solicitud
- **THEN** Inventory devuelve la misma reserva con los mismos datos de origen y presentación
