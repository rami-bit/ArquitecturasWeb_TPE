# Spec Delta

## ADDED Requirements

### Requirement: Informar la marca de alta demanda en el catálogo público

Las consultas públicas del catálogo SHALL informar si el evento está marcado como de alta demanda, tanto en el listado de eventos como en el detalle de un evento publicado, para que el cliente pueda ingresar a la fila virtual antes de intentar comprar.

#### Scenario: Listado de eventos con la marca

- **GIVEN** eventos publicados, algunos marcados como de alta demanda
- **WHEN** un cliente consulta el listado público de eventos
- **THEN** cada resultado informa la marca de alta demanda del evento

#### Scenario: Detalle de un evento de alta demanda

- **GIVEN** un evento publicado marcado como de alta demanda
- **WHEN** un cliente consulta su detalle público
- **THEN** la respuesta informa la marca de alta demanda del evento junto con su snapshot, participantes, fecha, lugar, sectores y precios

#### Scenario: Detalle de un evento que no está en la fila

- **GIVEN** un evento publicado sin marca de alta demanda
- **WHEN** un cliente consulta su detalle público
- **THEN** la respuesta informa que el evento no está marcado como de alta demanda y sigue sin afirmar disponibilidad de entradas
