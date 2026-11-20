# purchase-orders Specification

## Purpose

Permite a un cliente comprar entradas de un evento publicado mediante una orden idempotente que reserva lugares, cobra, confirma la venta y entrega tickets, compensando de forma observable los pagos rechazados, inciertos y las reservas vencidas.

## Requirements

### Requirement: Crear una orden de compra idempotente

Purchase SHALL crear una orden a partir de un `eventId`, uno o más ítems y un medio de pago, identificando al comprador por el `sub` del JWT validado y exigiendo una `Idempotency-Key`. La misma clave con el mismo contenido SHALL devolver la orden existente sin reservar, cobrar ni emitir de nuevo; la misma clave con contenido distinto SHALL rechazarse como conflicto. Trazabilidad: PDF-F4 y PDF-NF-SECURITY.

#### Scenario: Crear una compra nueva

- **WHEN** un cliente autenticado envía una orden válida con JWT, `Idempotency-Key` e ítems disponibles
- **THEN** Purchase devuelve la orden creada con su identificador y el estado resultante de la saga, asociada al `sub` autenticado

#### Scenario: Repetir la misma compra

- **GIVEN** una orden creada con una `Idempotency-Key`
- **WHEN** el mismo cliente repite la solicitud con la misma clave y el mismo contenido
- **THEN** Purchase devuelve la misma orden sin crear reservas, cobros ni tickets adicionales

#### Scenario: Reutilizar la clave con otro contenido

- **GIVEN** una orden creada con una `Idempotency-Key`
- **WHEN** el mismo cliente reutiliza la clave con otros ítems o evento
- **THEN** Purchase responde `409` con `application/problem+json` y no modifica la orden original

#### Scenario: Solicitud sin identidad o sin clave

- **WHEN** la solicitud no incluye un JWT válido o no incluye `Idempotency-Key`
- **THEN** el Gateway rechaza la identidad con `401`, o Purchase rechaza la clave faltante con `400`, y no se crea la orden

### Requirement: Validar los ítems contra el evento publicado

Purchase SHALL obtener del contrato interno de Catalog los datos y precios del evento publicado, SHALL rechazar eventos inexistentes, no publicados o suspendidos, y SHALL validar que cada ítem corresponda a un sector del evento con asientos numerados o cantidad de admisión general coherente.

#### Scenario: Evento inexistente o no publicado

- **WHEN** el cliente intenta comprar un evento inexistente, en borrador, suspendido o cancelado
- **THEN** Purchase responde `404` o `409` y no reserva ni cobra

#### Scenario: Ítem incoherente con el sector

- **WHEN** un ítem de sector numerado no incluye asientos o un ítem de admisión general no incluye cantidad
- **THEN** Purchase responde `400` y no crea la orden

#### Scenario: Precio tomado del snapshot del evento

- **WHEN** Purchase crea una orden para un evento publicado
- **THEN** el precio unitario de cada ítem proviene del snapshot de compra de Catalog y no del request del cliente

### Requirement: Reservar los lugares antes de cobrar

Purchase SHALL solicitar a Inventory una reserva temporal por el `orderId` antes de invocar el pago. Si Inventory rechaza la reserva por falta de disponibilidad, Purchase SHALL NOT cobrar y SHALL responder `409`.

#### Scenario: Reserva exitosa

- **GIVEN** un evento publicado con lugares disponibles
- **WHEN** Purchase reserva los ítems de la orden
- **THEN** Inventory devuelve una reserva con vencimiento y Purchase continúa con el cobro

#### Scenario: Sin disponibilidad

- **GIVEN** un asiento o cupo ya retenido o vendido
- **WHEN** Purchase intenta reservarlo para una orden nueva
- **THEN** Inventory responde `409`, Purchase no invoca Payment y la orden no queda confirmada

#### Scenario: Reintento de reserva

- **GIVEN** una reserva creada para una orden
- **WHEN** Purchase repite la reserva por un reintento de red
- **THEN** Inventory devuelve la misma reserva y Purchase no retiene lugares adicionales

### Requirement: Cobrar mediante la pasarela simulada

Purchase SHALL invocar Payment Mock con el `orderId`, el monto total y el medio de pago después de reservar. Payment SHALL recibir una clave idempotente derivada de la orden y Purchase SHALL registrar el identificador y estado del pago en la orden.

#### Scenario: Pago aprobado

- **GIVEN** una reserva activa y un medio de pago aprobado por la simulación
- **WHEN** Purchase solicita el cobro
- **THEN** Payment devuelve un pago `APPROVED` y Purchase continúa con la confirmación de inventario

#### Scenario: Pago rechazado

- **GIVEN** una reserva activa y un medio de pago rechazado por la simulación
- **WHEN** Purchase solicita el cobro
- **THEN** Payment devuelve un pago `REJECTED`, Purchase libera la reserva y responde `402` con la orden rechazada

#### Scenario: Reintento de cobro

- **GIVEN** un pago ya registrado para una orden
- **WHEN** Purchase repite el cobro con la misma clave
- **THEN** Payment devuelve el pago existente sin cobrar dos veces

### Requirement: Confirmar inventario y emitir tickets

Purchase SHALL confirmar la reserva en Inventory cuando el pago sea aprobado y SHALL emitir tickets por cada ítem confirmado. La orden SHALL quedar `CONFIRMED` sólo cuando la emisión de todos los tickets haya finalizado correctamente.

#### Scenario: Compra confirmada

- **GIVEN** un pago aprobado y una reserva vigente
- **WHEN** Purchase confirma Inventory y emite los tickets
- **THEN** la orden queda `CONFIRMED`, los lugares quedan vendidos y la respuesta incluye los tickets con su QR

#### Scenario: Emisión idempotente

- **GIVEN** una orden confirmada con tickets emitidos
- **WHEN** Purchase repite la emisión por un reintento
- **THEN** Ticket Service devuelve los mismos tickets y Purchase conserva una única emisión por ítem

#### Scenario: Fallo transitorio de emisión

- **GIVEN** un pago aprobado y el inventario confirmado
- **WHEN** Ticket Service no responde al emitir
- **THEN** la orden queda `PAID_PENDING_TICKETS` y Purchase permite reconciliarla sin volver a cobrar

### Requirement: Compensar una reserva vencida o una confirmación fallida

Purchase SHALL solicitar el reintegro a Payment y la liberación idempotente a Inventory cuando el pago fue aprobado pero la reserva ya no puede confirmarse. La orden SHALL quedar en un estado compensado y SHALL NOT exponer tickets.

#### Scenario: Pago aprobado con reserva vencida

- **GIVEN** un pago aprobado cuya reserva expiró antes de la confirmación
- **WHEN** Inventory rechaza la confirmación con `409`
- **THEN** Purchase solicita el reintegro, libera la reserva si sigue activa, marca la orden como compensada y responde `409`

#### Scenario: Reintento de la compensación

- **GIVEN** una orden compensada
- **WHEN** Purchase repite el reintegro y la liberación
- **THEN** Payment e Inventory devuelven el mismo resultado sin duplicar movimientos

#### Scenario: Compensación no expone tickets

- **WHEN** una orden queda compensada
- **THEN** la respuesta y las consultas posteriores no incluyen tickets emitidos

### Requirement: Manejar un pago incierto y reconciliar la orden

Purchase SHALL dejar la orden en `PAYMENT_PENDING` cuando Payment no confirme aprobación ni rechazo, y SHALL exponer una operación de reconciliación que consulte el pago y continúe o compense la orden de forma idempotente.

#### Scenario: Pago en estado pendiente

- **GIVEN** una reserva activa
- **WHEN** Payment devuelve `PENDING` o no responde dentro del tiempo de espera
- **THEN** Purchase responde `202`, conserva la reserva y deja la orden en `PAYMENT_PENDING`

#### Scenario: Reconciliar un pago que se aprobó

- **GIVEN** una orden en `PAYMENT_PENDING` cuyo pago luego figura `APPROVED`
- **WHEN** el cliente solicita la reconciliación
- **THEN** Purchase confirma inventario, emite tickets y devuelve la orden `CONFIRMED`

#### Scenario: Reconciliar un pago que se rechazó

- **GIVEN** una orden en `PAYMENT_PENDING` cuyo pago luego figura `REJECTED`
- **WHEN** el cliente solicita la reconciliación
- **THEN** Purchase libera la reserva y devuelve la orden `REJECTED`

#### Scenario: Reconciliar una orden no reconciliable

- **GIVEN** una orden `CONFIRMED` o `REJECTED`
- **WHEN** se solicita reconciliarla
- **THEN** Purchase responde `409` sin modificar la orden

### Requirement: Limitar las entradas por cliente y evento

Purchase SHALL aplicar un límite configurable de entradas por cliente y evento, seis por defecto, contabilizando órdenes confirmadas y pendientes. La validación SHALL ser atómica para que solicitudes concurrentes no superen el límite.

#### Scenario: Compra dentro del límite

- **GIVEN** un cliente sin compras previas del evento
- **WHEN** compra una cantidad menor o igual al límite
- **THEN** Purchase crea la orden y contabiliza las unidades reservadas

#### Scenario: Compra que excede el límite

- **GIVEN** un cliente que ya alcanzó el límite de entradas del evento
- **WHEN** intenta comprar una unidad adicional
- **THEN** Purchase responde `409` y no reserva ni cobra

#### Scenario: Compras concurrentes del mismo cliente

- **GIVEN** un cliente con una unidad disponible dentro del límite
- **WHEN** dos compras concurrentes intentan consumirla
- **THEN** sólo una orden contabiliza la unidad y la otra recibe `409`

#### Scenario: Liberar el límite al rechazar la compra

- **GIVEN** una orden pendiente que contabilizó unidades
- **WHEN** el pago se rechaza o la orden se compensa
- **THEN** Purchase libera esas unidades del límite sin afectar compras confirmadas

### Requirement: Consultar órdenes propias

Purchase SHALL permitir al comprador consultar una orden por identificador y listar sus propias órdenes, identificándolo mediante el `sub` del JWT validado. Un cliente SHALL NOT ver órdenes de otro comprador; un administrador SHALL poder consultarlas. Trazabilidad: PDF-F4 y PDF-NF-SECURITY.

#### Scenario: Consultar la propia orden

- **GIVEN** una orden creada por el cliente identificado por el `sub` del JWT validado
- **WHEN** el cliente solicita `GET /api/orders/{orderId}`
- **THEN** Purchase devuelve la orden con sus ítems, pago y tickets

#### Scenario: Consultar una orden ajena

- **GIVEN** una orden de otro comprador
- **WHEN** un cliente la solicita por identificador
- **THEN** Purchase responde `404` sin revelar su existencia

#### Scenario: Listar órdenes propias

- **WHEN** un cliente solicita `GET /api/orders` con un JWT válido
- **THEN** Purchase devuelve únicamente las órdenes asociadas a su `sub`, paginadas

#### Scenario: Administrador consulta órdenes

- **WHEN** un administrador autorizado solicita órdenes
- **THEN** Purchase permite consultar órdenes de cualquier comprador conforme a las reglas vigentes

### Requirement: Errores consistentes y trazabilidad de la compra

Purchase SHALL responder los errores con `application/problem+json` incluyendo `status`, `title`, `detail`, `instance` y `correlationId`. SHALL conservar o generar `X-Correlation-Id`, propagarlo a Catalog, Inventory, Payment y Ticket, y registrar cada transición de la orden sin exponer datos de pago sensibles.

#### Scenario: Propagar el correlation ID

- **WHEN** una solicitud llega con `X-Correlation-Id: trace-purchase-1`
- **THEN** Purchase lo propaga a todos los servicios que invoca y lo devuelve en la respuesta

#### Scenario: Error de dependencia

- **GIVEN** la operación tiene un fallo explícito de dependencia, no una respuesta de cobro perdida o una emisión QR pendiente
- **WHEN** Catalog, Inventory o Payment Mock responden un error HTTP de servidor, o falla la consulta de Payment durante la reconciliación
- **THEN** Purchase responde `503` como `application/problem+json`, no deja una orden confirmada parcialmente y libera o conserva las reservas y límites según el estado persistido de la saga

Un timeout o pérdida de respuesta durante el cobro es incierto y se rige por
`Manejar un pago incierto y reconciliar la orden` (`202 PAYMENT_PENDING`); una
falla transitoria de Ticket se rige por `Confirmar inventario y emitir tickets`
(`202 PAID_PENDING_TICKETS`).

#### Scenario: No exponer datos sensibles

- **WHEN** Purchase registra o responde una compra
- **THEN** no incluye tokens de pago, payloads QR completos ni credenciales en logs

### Requirement: Ownership y snapshots históricos de la orden

Purchase SHALL ser el único dueño de `purchase_db`. SHALL guardar un snapshot de nombre, fecha, lugar, sector, precio y unidad al crear la orden, SHALL referenciar otros servicios sólo por identificadores y SHALL NOT acceder directamente a sus bases ni crear foreign keys entre servicios. SHALL exponer a Preferences un contrato interno de señales agregadas de historial, sin órdenes individuales ni datos de pago. Trazabilidad: PDF-F10.

#### Scenario: Conservar el precio de la compra

- **GIVEN** un evento cuyo precio cambia después de la compra
- **WHEN** se consulta la orden
- **THEN** Purchase devuelve el precio y los datos históricos guardados al crear la orden

#### Scenario: Referencias sin foreign keys cruzadas

- **WHEN** se inspecciona `purchase_db`
- **THEN** las referencias a evento, sector, asiento, pago y tickets son valores UUID sin constraints hacia otras bases

#### Scenario: Señales de historial de un cliente

- **GIVEN** un cliente con compras pagadas de dos eventos
- **WHEN** Preferences solicita `GET /internal/customers/{customerId}/recommendation-signals`
- **THEN** Purchase responde `200` con esos eventos en `purchasedEventIds` y las co-compras agregadas del cliente, sin montos, pagos ni tickets

#### Scenario: Sólo compras pagadas cuentan

- **GIVEN** órdenes del cliente en estado `REJECTED`, `PAYMENT_PENDING` o `COMPENSATED`
- **WHEN** se solicitan las señales del cliente
- **THEN** esos eventos no aparecen en `purchasedEventIds` ni alimentan las co-compras

#### Scenario: Cliente sin compras

- **WHEN** se solicitan las señales de un cliente sin órdenes pagadas
- **THEN** Purchase responde `200` con `purchasedEventIds` y `coPurchases` vacías, sin responder `404`

#### Scenario: Señales acotadas

- **GIVEN** un cliente con historial que produce más de 200 eventos candidatos de co-compra
- **WHEN** se solicitan sus señales
- **THEN** Purchase devuelve como máximo 200 co-compras ordenadas por `count` descendente y `eventId` ascendente

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
