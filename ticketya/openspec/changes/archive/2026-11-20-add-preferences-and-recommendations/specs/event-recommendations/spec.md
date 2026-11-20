# Spec Delta

## Purpose

Permite que cada cliente autenticado reciba una lista acotada de eventos publicados y futuros, ordenada por una afinidad explicable con sus preferencias y su historial de compras, sin garantizar disponibilidad y degradando de forma controlada cuando falta una dependencia.

## ADDED Requirements

### Requirement: Recomendar eventos publicados y futuros

El servicio SHALL devolver, mediante `GET /api/customers/me/recommendations`, hasta `limit` eventos `PUBLISHED` con `startsAt` futuro que el cliente no haya comprado, ordenados por afinidad. `limit` SHALL ser `10` por omisión y SHALL aceptar sólo valores entre `1` y `20`. Cada elemento SHALL informar el evento, su `score` y sus `reasons`. Trazabilidad: PDF-F10.

#### Scenario: Cliente con preferencias e historial

- **GIVEN** un cliente con preferencias registradas y compras confirmadas
- **WHEN** consulta `GET /api/customers/me/recommendations`
- **THEN** el servicio responde `200` con eventos publicados y futuros, cada uno con `eventId`, `name`, `eventType`, `startsAt`, `city`, `participants`, `minPrice`, `score` y `reasons`

#### Scenario: Límite por omisión

- **GIVEN** un cliente con más de diez candidatos afines
- **WHEN** consulta las recomendaciones sin informar `limit`
- **THEN** el servicio devuelve exactamente diez elementos

#### Scenario: Límite fuera de rango

- **WHEN** un cliente informa `limit=0`, `limit=21` o un valor no numérico
- **THEN** el servicio responde `400` con `application/problem+json` e identifica el parámetro `limit`

#### Scenario: Evento ya comprado

- **GIVEN** un cliente con una compra confirmada de un evento publicado y futuro
- **WHEN** consulta sus recomendaciones
- **THEN** ese evento no aparece en la lista aunque tenga afinidad alta

#### Scenario: Evento no publicado o pasado

- **GIVEN** un evento en `DRAFT`, `SUSPENDED` o con `startsAt` anterior al instante de la consulta
- **WHEN** un cliente consulta sus recomendaciones
- **THEN** ese evento no aparece en la lista

#### Scenario: Consulta sin identidad

- **WHEN** un cliente consulta las recomendaciones sin JWT válido
- **THEN** el Gateway responde `401` con `application/problem+json` y no invoca al servicio de preferencias

### Requirement: Calcular un puntaje explicable

El puntaje SHALL sumar `3` por tipo favorito, `2` por artista del evento en favoritos con tope de tres artistas, `2` por coincidir la ciudad, `min(5, coCompras)` y `1` si el evento ocurre dentro de 30 días. Cada componente SHALL informarse en `reasons` como `FAVORITE_EVENT_TYPE`, `FAVORITE_ARTIST`, `SAME_CITY`, `CO_PURCHASE` o `UPCOMING_FALLBACK`. Sin machine learning. Trazabilidad: PDF-F10.

#### Scenario: Evento que coincide con todas las señales

- **GIVEN** un cliente que prefiere el tipo y la ciudad del evento, con tres de sus artistas favoritos en el cartel, cinco co-compras y un evento dentro de 30 días
- **WHEN** el servicio puntúa ese candidato
- **THEN** el `score` es `17` y `reasons` contiene los cinco motivos

#### Scenario: Sólo coincidencia de tipo

- **GIVEN** un candidato que sólo coincide en el tipo de evento favorito y ocurre en más de 30 días
- **WHEN** el servicio lo puntúa
- **THEN** el `score` es `3` y `reasons` contiene únicamente `FAVORITE_EVENT_TYPE`

#### Scenario: Artistas repetidos con tope

- **GIVEN** un candidato con cinco artistas favoritos del cliente
- **WHEN** el servicio lo puntúa
- **THEN** el componente de artistas aporta `6`, porque el tope es de tres artistas

#### Scenario: Comparación sin distinguir mayúsculas

- **GIVEN** un cliente que prefiere el tipo `Concierto` y un evento cuyo tipo es `CONCIERTO`
- **WHEN** el servicio puntúa ese candidato
- **THEN** el tipo se considera coincidente y suma `3` con el motivo `FAVORITE_EVENT_TYPE`

#### Scenario: Candidato sin afinidad

- **GIVEN** un candidato que no coincide en tipo, artistas ni ciudad, sin co-compras y con fecha a más de 30 días
- **WHEN** el servicio lo puntúa
- **THEN** su `score` es `0` y no se incluye en la respuesta

### Requirement: Usar el historial de compras como señal de co-compra

El servicio SHALL obtener el historial del contrato interno de Purchase y SHALL contar, para cada candidato no comprado, la cantidad de clientes distintos que compraron ese evento y algún evento del cliente. Sólo SHALL contar órdenes `CONFIRMED` o `PAID_PENDING_TICKETS`. La señal SHALL acotarse a 200 co-compras. Trazabilidad: PDF-F10.

#### Scenario: Co-compra puntúa

- **GIVEN** cuatro clientes que compraron un evento del cliente y además un candidato
- **WHEN** el servicio puntúa ese candidato
- **THEN** el componente de co-compra aporta `4` y `reasons` incluye `CO_PURCHASE`

#### Scenario: Co-compra con tope

- **GIVEN** un candidato con ocho clientes en común
- **WHEN** el servicio lo puntúa
- **THEN** el componente de co-compra aporta `5`, porque el tope es `min(5, coCompras)`

#### Scenario: Órdenes no pagadas no cuentan

- **GIVEN** órdenes en estado `REJECTED`, `PAYMENT_PENDING` o `COMPENSATED`
- **WHEN** el servicio obtiene el historial del cliente
- **THEN** esos eventos no cuentan como comprados ni como co-compra

#### Scenario: Cliente sin historial

- **GIVEN** un cliente sin órdenes pagadas
- **WHEN** el servicio consulta sus señales
- **THEN** Purchase responde `200` con listas vacías y el componente de co-compra no suma en ningún candidato

### Requirement: Obtener los candidatos del contrato interno de Catalog

El servicio SHALL obtener los candidatos del contrato interno de Catalog, que SHALL entregar eventos `PUBLISHED` con fecha futura, con tipo, ciudad, participantes y precio mínimo, acotados a 200 por proximidad. El servicio SHALL NOT consultar la base del catálogo ni afirmar disponibilidad. Trazabilidad: PDF-F10 y PDF-NF-CONSISTENCY.

#### Scenario: Candidatos acotados y ordenados

- **GIVEN** más de 200 eventos publicados y futuros
- **WHEN** el servicio solicita los candidatos con `limit=200`
- **THEN** Catalog devuelve los 200 más próximos por `startsAt` ascendente y `eventId` ascendente

#### Scenario: Candidatos sin disponibilidad

- **WHEN** el servicio obtiene un candidato del catálogo
- **THEN** el candidato informa tipo, fecha, ciudad, participantes y precio mínimo, y no informa cupos ni stock

#### Scenario: Catalog no disponible

- **GIVEN** que Catalog no responde o excede el tiempo de espera
- **WHEN** un cliente consulta sus recomendaciones
- **THEN** el servicio responde `503` con `application/problem+json` y `X-Correlation-Id`

### Requirement: Degradar la recomendación cuando falta el historial

Si Purchase no responde o excede el tiempo de espera, el servicio SHALL responder `200` con recomendaciones calculadas sólo por preferencias y la advertencia `HISTORY_UNAVAILABLE`, sin reintentos automáticos. Si Catalog no responde, SHALL responder `503`. Trazabilidad: PDF-NF-RESILIENCE.

#### Scenario: Purchase no disponible

- **GIVEN** un cliente con preferencias registradas
- **WHEN** Purchase falla durante la consulta de recomendaciones
- **THEN** el servicio responde `200` con recomendaciones por preferencias y `warnings` igual a `["HISTORY_UNAVAILABLE"]`

#### Scenario: Purchase excede el tiempo de espera

- **GIVEN** un cliente con preferencias registradas
- **WHEN** Purchase tarda más que el tiempo de lectura configurado
- **THEN** el servicio responde `200` con la misma degradación y no reintenta la llamada

#### Scenario: Sin preferencias ni historial

- **GIVEN** un cliente sin preferencias registradas y sin compras
- **WHEN** Purchase no está disponible
- **THEN** el servicio responde `200` con los próximos eventos y el motivo `UPCOMING_FALLBACK`

### Requirement: Devolver un orden determinista y limitado

El servicio SHALL ordenar por `score` descendente, `startsAt` ascendente y `eventId` ascendente, y SHALL aplicar el límite después de ordenar. Dos consultas con los mismos datos SHALL devolver la misma lista en el mismo orden. Trazabilidad: PDF-F10.

#### Scenario: Empate de puntaje

- **GIVEN** dos candidatos con el mismo `score` y fechas distintas
- **WHEN** el servicio ordena la respuesta
- **THEN** aparece primero el de `startsAt` más próximo

#### Scenario: Empate de puntaje y fecha

- **GIVEN** dos candidatos con el mismo `score` y el mismo `startsAt`
- **WHEN** el servicio ordena la respuesta
- **THEN** aparece primero el de `eventId` menor

#### Scenario: Límite aplicado después del orden

- **GIVEN** diez candidatos afines y `limit=3`
- **WHEN** el servicio responde
- **THEN** devuelve los tres primeros del orden por afinidad, no los tres primeros por fecha de creación

### Requirement: Recomendar sin garantizar disponibilidad

Las recomendaciones SHALL ser informativas: SHALL NOT reservar unidades, SHALL NOT descontar inventario y SHALL NOT afirmar que el evento tiene entradas disponibles. Un evento agotado SHALL poder aparecer si su afinidad lo justifica. Trazabilidad: PDF-F10 y PDF-NF-CONSISTENCY.

#### Scenario: Evento agotado recomendado

- **GIVEN** un evento publicado y futuro sin entradas disponibles
- **WHEN** un cliente con afinidad consulta sus recomendaciones
- **THEN** el evento puede aparecer en la lista y la respuesta no afirma disponibilidad

#### Scenario: Recomendar no reserva

- **WHEN** un cliente consulta sus recomendaciones
- **THEN** no se crea ninguna reserva, orden ni descuento de inventario

### Requirement: Ofrecer un fallback cuando no hay señales

Cuando el cliente no tiene preferencias ni historial, el servicio SHALL devolver los próximos eventos publicados y futuros con el motivo `UPCOMING_FALLBACK` en lugar de una lista vacía. Si no hay candidatos, SHALL responder `200` con la lista vacía. Trazabilidad: PDF-F10.

#### Scenario: Cliente nuevo

- **GIVEN** un cliente sin preferencias y sin compras
- **WHEN** consulta sus recomendaciones
- **THEN** el servicio devuelve los próximos eventos publicados y futuros, cada uno con `score` `0` y `reasons` igual a `["UPCOMING_FALLBACK"]`

#### Scenario: Catálogo sin eventos futuros

- **GIVEN** que no existen eventos publicados con fecha futura
- **WHEN** un cliente consulta sus recomendaciones
- **THEN** el servicio responde `200` con `items` vacío y sin advertencias

### Requirement: Errores consistentes y trazabilidad de recomendaciones

Las respuestas de error SHALL usar `application/problem+json` con `type`, `title`, `status`, `detail`, `instance` y `correlationId`. Cada consulta SHALL registrar el cliente, la cantidad de candidatos, la cantidad devuelta, las advertencias y el tiempo de procesamiento, sin registrar el contenido de las preferencias. Trazabilidad: PDF-NF-TRACE.

#### Scenario: Error con correlation ID

- **WHEN** un cliente recibe un error de recomendaciones
- **THEN** la respuesta es `application/problem+json`, incluye `correlationId` y devuelve el mismo `X-Correlation-Id` propagado en la solicitud

#### Scenario: Registro de la consulta

- **WHEN** un cliente consulta sus recomendaciones
- **THEN** el servicio registra cliente, cantidad de candidatos, cantidad devuelta, advertencias y duración, sin registrar el contenido de las preferencias
