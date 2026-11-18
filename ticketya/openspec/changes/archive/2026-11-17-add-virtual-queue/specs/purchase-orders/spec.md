# Spec Delta

## ADDED Requirements

### Requirement: Exigir admisión para comprar en eventos de alta demanda

Purchase SHALL exigir un token de admisión válido cuando el evento publicado está marcado como de alta demanda, y SHALL NOT exigirlo en los demás eventos. El token SHALL estar ligado al evento y al cliente y SHALL habilitar el intento de compra sin reservar ni garantizar inventario.

#### Scenario: Compra de un evento de alta demanda con token válido

- **GIVEN** un evento publicado marcado como de alta demanda
- **WHEN** un cliente autenticado envía una orden con un token de admisión válido para ese evento
- **THEN** Purchase continúa la compra con la reserva, el cobro y la emisión habituales

#### Scenario: Compra de un evento de alta demanda sin token

- **GIVEN** un evento publicado marcado como de alta demanda
- **WHEN** un cliente autenticado envía una orden sin token de admisión
- **THEN** Purchase responde `403` con `application/problem+json` y no reserva, no cobra ni emite entradas

#### Scenario: Token de admisión de otro cliente

- **GIVEN** un token de admisión emitido a un cliente
- **WHEN** otro cliente lo presenta al comprar
- **THEN** Purchase responde `403` con `application/problem+json` y no reserva, no cobra ni emite entradas

#### Scenario: Token de admisión vencido

- **GIVEN** un token de admisión cuya vigencia ya venció
- **WHEN** su titular lo presenta al comprar
- **THEN** Purchase responde `403` con `application/problem+json` y no reserva, no cobra ni emite entradas

#### Scenario: Token de admisión ya utilizado

- **GIVEN** un token de admisión ya utilizado para crear una orden
- **WHEN** su titular lo vuelve a presentar con una `Idempotency-Key` distinta
- **THEN** Purchase responde `403` con `application/problem+json` y no crea una segunda orden

#### Scenario: Compra de un evento que no es de alta demanda

- **GIVEN** un evento publicado sin marca de alta demanda
- **WHEN** un cliente autenticado envía una orden sin token de admisión
- **THEN** Purchase crea la orden con el comportamiento habitual

#### Scenario: Reintento idempotente con el mismo token

- **GIVEN** una orden creada con un token de admisión y una `Idempotency-Key`
- **WHEN** el mismo cliente repite la solicitud con la misma clave y el mismo contenido
- **THEN** Purchase devuelve la orden existente sin consumir de nuevo el token de admisión ni crear reservas, cobros o tickets adicionales

#### Scenario: Evento que dejó de estar en venta

- **GIVEN** un evento de alta demanda que fue cancelado o suspendido
- **WHEN** un cliente presenta un token de admisión válido de ese evento
- **THEN** Purchase responde `404` por evento no comprable y no reserva, no cobra ni emite entradas
