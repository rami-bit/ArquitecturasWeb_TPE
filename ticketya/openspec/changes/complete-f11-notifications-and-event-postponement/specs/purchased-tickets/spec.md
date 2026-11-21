# Spec Delta

## ADDED Requirements

### Requirement: Mostrar la programación vigente de una entrada comprada

La vista de una orden pagada con entradas SHALL conservar `eventStartsAt` como fecha capturada al comprar y SHALL añadir `currentEventStartsAt` con la fecha vigente informada por Catalog. Una postergación SHALL NOT cambiar el identificador, firma ni payload QR del ticket. Trazabilidad: PDF-F4, PDF-F8 y PDF-F11.

#### Scenario: Titular consulta tras una postergación

- **WHEN** el titular consulta su orden y entradas luego de postergar el evento
- **THEN** ve la fecha original en `eventStartsAt`, la nueva en `currentEventStartsAt` y los mismos tickets y QR

#### Scenario: Catalog no disponible durante la consulta

- **WHEN** Catalog no permite obtener la programación vigente
- **THEN** Purchase conserva la consulta de entradas y QR, informa `currentEventStartsAt` nulo y una advertencia de fecha no disponible, sin presentar la fecha histórica como vigente

#### Scenario: Otro cliente consulta la orden

- **WHEN** un cliente distinto del titular solicita la vista con programación y QR
- **THEN** se mantiene el `404` existente y no se revelan fechas ni entradas de la orden ajena
