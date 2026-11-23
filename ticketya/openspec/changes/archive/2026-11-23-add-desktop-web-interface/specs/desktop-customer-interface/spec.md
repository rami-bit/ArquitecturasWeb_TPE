# Spec Delta

## Purpose

Permite al cliente recorrer en escritorio la búsqueda, selección y compra de entradas, y consultar posteriormente sus órdenes, tickets y devoluciones mediante la API pública de TicketYa.

## ADDED Requirements

### Requirement: Descubrir eventos publicados

La interfaz SHALL permitir buscar eventos publicados por texto, tipo, organizador, participante, ciudad y rango de fechas, y ordenar y paginar los resultados según el contrato público de Catalog. Trazabilidad: PDF-F9 y PDF-INTERFAZ-GRAFICA.

#### Scenario: Buscar con filtros
- **WHEN** el usuario combina filtros admitidos y ejecuta la búsqueda
- **THEN** la interfaz muestra los resultados, el orden y la paginación devueltos por `GET /api/events`

#### Scenario: Consulta inválida o vacía
- **WHEN** la API devuelve un error de parámetros o una página sin resultados
- **THEN** la interfaz explica el error o el resultado vacío sin inventar eventos

### Requirement: Mostrar detalle y disponibilidad por separado

La interfaz SHALL mostrar datos y precios del detalle público de Catalog y disponibilidad actual de Inventory, distinguiendo sectores numerados y de admisión general. SHALL usar los identificadores de sector y asiento de la API y no presentar la selección como reserva confirmada. Trazabilidad: PDF-F4 y PDF-F5.

#### Scenario: Selección numerada
- **WHEN** el usuario abre un evento numerado y selecciona asientos disponibles
- **THEN** la interfaz conserva sus `seatIds` y el `sectorId` correspondiente para el intento de compra

#### Scenario: Admisión general
- **WHEN** el usuario elige un sector no numerado y una cantidad válida
- **THEN** la interfaz prepara ese `sectorId` y `quantity` sin `seatIds`

#### Scenario: Disponibilidad cambiante
- **WHEN** Inventory devuelve disponibilidad nueva o Purchase responde conflicto de cupo
- **THEN** la interfaz actualiza el estado visible y pide revisar la selección antes de un nuevo intento

### Requirement: Respetar la fila virtual

Para eventos de alta demanda, la interfaz SHALL permitir entrar a la fila, consultar el turno propio y usar su token de admisión vigente en el intento de compra. SHALL aclarar que el turno no garantiza entradas. Trazabilidad: PDF-F6.

#### Scenario: Turno en espera
- **WHEN** el cliente entra a una fila y su turno está `WAITING`
- **THEN** la interfaz muestra posición/estado y consulta periódicamente el turno propio sin habilitar la compra

#### Scenario: Turno admitido
- **WHEN** la API informa `ADMITTED` con token y vencimiento
- **THEN** la interfaz habilita el intento de compra con `X-Admission-Token` hasta su vencimiento

#### Scenario: Admisión vencida o rechazada
- **WHEN** vence el token o Purchase rechaza la admisión
- **THEN** la interfaz deja de ofrecer ese token para nuevas órdenes e indica cómo consultar o volver a la fila

### Requirement: Comprar sin duplicar una intención

La interfaz SHALL crear órdenes mediante `POST /api/orders` con `Idempotency-Key`, ítems del sector elegido y un token de pago de la pasarela simulada. SHALL conservar la misma clave y cuerpo para repetir una intención incierta; SHALL generar otra clave sólo para una selección o intento nuevo. Trazabilidad: PDF-F4 y PDF-F5.

#### Scenario: Compra confirmada
- **WHEN** Purchase confirma la orden
- **THEN** la interfaz muestra identificador, monto, estado y entradas emitidas con sus QR

#### Scenario: Respuesta perdida o pendiente
- **WHEN** la compra queda `PAYMENT_PENDING` o `PAID_PENDING_TICKETS`, o se pierde la respuesta
- **THEN** la interfaz conserva la intención, permite consultar o reconciliar la orden conocida y evita crear automáticamente otra compra

#### Scenario: Pago rechazado
- **WHEN** Purchase responde pago rechazado y orden `REJECTED`
- **THEN** la interfaz informa que no hay entradas emitidas y permite iniciar otro intento explícito

#### Scenario: Límite o cupo rechazado
- **WHEN** Purchase rechaza la cantidad, el límite por cliente o la disponibilidad
- **THEN** la interfaz muestra la causa devuelta y permite corregir la selección

### Requirement: Consultar órdenes y entradas propias

La interfaz SHALL construir «Mis entradas» desde las órdenes visibles para el JWT del cliente y sus tickets, sin depender de una ruta pública de listado de tickets. SHALL mostrar estado de cada orden y QR sólo cuando exista una entrada emitida. Trazabilidad: PDF-F4 y PDF-F7.

#### Scenario: Cliente consulta sus compras
- **WHEN** un cliente abre Mis entradas
- **THEN** la interfaz lista sus órdenes y permite abrir el detalle con los tickets emitidos

#### Scenario: Orden sin tickets
- **WHEN** una orden está pendiente, rechazada o compensada sin entradas emitidas
- **THEN** la interfaz muestra su estado sin fabricar QR

### Requirement: Solicitar devoluciones propias

La interfaz SHALL permitir solicitar la devolución de un ticket propio y mostrar el estado devuelto por Purchase, respetando la regla de 72 horas evaluada por el servidor. Trazabilidad: PDF-F7.

#### Scenario: Solicitud aceptada
- **WHEN** el cliente solicita la devolución de un ticket elegible
- **THEN** la interfaz muestra la solicitud y su estado para seguimiento

#### Scenario: Solicitud fuera de plazo
- **WHEN** Purchase rechaza la solicitud por plazo u otra regla de dominio
- **THEN** la interfaz explica el rechazo sin alterar el ticket mostrado

### Requirement: Consultar preferencias y recomendaciones

La interfaz SHALL permitir al cliente editar sus preferencias y consultar recomendaciones, sin presentar estas últimas como garantía de disponibilidad. Trazabilidad: PDF-F10.

#### Scenario: Guardar preferencias
- **WHEN** el cliente guarda ciudad, tipos o artistas favoritos válidos
- **THEN** la interfaz muestra los valores confirmados por la API

#### Scenario: Abrir una recomendación
- **WHEN** el cliente selecciona un evento recomendado
- **THEN** la interfaz abre su detalle público y consulta disponibilidad actual antes de ofrecer compra

### Requirement: Registrar email y mostrar la fecha vigente del evento

La interfaz SHALL permitir al `CLIENT` registrar y consultar su email propio por las rutas F11. En «Mis entradas», SHALL conservar `eventStartsAt` de la orden como fecha histórica y consultar `GET /api/events/{id}` por `eventId` para mostrar el `startsAt` vigente cuando el detalle público esté disponible. SHALL advertir cuando no pueda obtener la fecha vigente, sin inferirla de la orden. Trazabilidad: PDF-F11.

#### Scenario: Registrar y consultar email propio
- **WHEN** el cliente guarda y vuelve a consultar su dirección de contacto
- **THEN** la interfaz muestra el email confirmado por `PUT` y `GET /api/notifications/me/email`

#### Scenario: Evento postergado con detalle público disponible
- **WHEN** la orden contiene un `eventStartsAt` histórico y el detalle público del mismo `eventId` devuelve un `startsAt` posterior
- **THEN** la interfaz destaca la fecha vigente de Catalog y conserva visible la fecha histórica como dato de compra

#### Scenario: Fecha vigente no disponible
- **WHEN** Catalog devuelve `404` para un evento no visible públicamente o falla la consulta del detalle
- **THEN** la interfaz indica que no pudo obtener la fecha vigente y muestra `eventStartsAt` sólo como fecha histórica
