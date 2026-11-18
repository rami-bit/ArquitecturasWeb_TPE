# virtual-queue Specification

## Purpose
Define la fila virtual de los eventos de alta demanda: la entrada del cliente, su posición, la apertura de tandas en orden y el token de admisión de un solo uso que habilita el intento de compra sin garantizar inventario.

## Requirements

### Requirement: Entrar a la fila de un evento de alta demanda

La fila SHALL aceptar la entrada de un cliente a un evento marcado como de alta demanda y devolverle su posición, el tamaño de la tanda vigente y el estado de su turno. La entrada SHALL ser idempotente: un cliente que ya tiene un turno activo en ese evento SHALL obtener el mismo turno y la misma posición, sin ocupar un lugar adicional en la fila.

#### Scenario: Cliente entra por primera vez

- **GIVEN** un evento publicado marcado como de alta demanda
- **WHEN** un cliente autenticado entra a la fila de ese evento
- **THEN** la fila devuelve su turno con una posición, el tamaño de tanda vigente y el estado `WAITING`, y no crea un segundo turno para el mismo cliente en el mismo evento

#### Scenario: Cliente que ya tiene turno vuelve a entrar

- **GIVEN** un cliente con un turno activo en la fila de un evento
- **WHEN** el mismo cliente vuelve a entrar a la fila de ese evento
- **THEN** la fila devuelve el turno existente con la misma posición y no altera el orden de la fila

#### Scenario: Evento que no está en la fila

- **GIVEN** un evento publicado que no está marcado como de alta demanda
- **WHEN** un cliente autenticado intenta entrar a su fila
- **THEN** la fila responde `409` con `application/problem+json` y no crea ningún turno

#### Scenario: Evento que no está en venta

- **GIVEN** un evento inexistente, en borrador, suspendido o cancelado
- **WHEN** un cliente autenticado intenta entrar a su fila
- **THEN** la fila responde `404` con `application/problem+json` sin revelar datos del evento

#### Scenario: Cliente sin rol habilitado

- **WHEN** un usuario autenticado con un rol distinto de `CLIENT` o `ADMIN` intenta entrar a la fila
- **THEN** la fila responde `403` con `application/problem+json` y no crea ningún turno

### Requirement: Informar la posición y el estado del turno

La fila SHALL informar a cada cliente el estado de su turno: la posición que ocupa, la cantidad de clientes que lo preceden, el tamaño de la tanda vigente y, cuando su turno fue admitido, el token de admisión con su vencimiento. La fila SHALL NOT informar la disponibilidad de entradas ni la cantidad de lugares libres del evento.

#### Scenario: Consultar un turno en espera

- **GIVEN** un cliente con un turno activo en estado `WAITING`
- **WHEN** el cliente consulta su turno
- **THEN** la fila devuelve su posición, la tanda vigente y el estado `WAITING`, sin token de admisión

#### Scenario: Consultar un turno admitido

- **GIVEN** un cliente cuyo turno fue admitido
- **WHEN** el cliente consulta su turno
- **THEN** la fila devuelve el token de admisión, su instante de vencimiento y el estado `ADMITTED`

#### Scenario: Consultar un turno ajeno

- **GIVEN** un cliente autenticado sin turno en la fila de un evento
- **WHEN** el cliente consulta su turno en ese evento
- **THEN** la fila responde `404` con `application/problem+json` sin revelar el turno de otro cliente

### Requirement: Habilitar las tandas en orden

La fila SHALL habilitar el acceso a la compra por tandas, respetando el orden de llegada: en cada apertura SHALL admitir a los clientes que erwartan, empezando por la primera posición, hasta completar el tamaño de la tanda vigente, y SHALL NOT admitir a un cliente antes que otro que lo precede en la fila. La fila SHALL NOT reservar ni garantizar inventario: un cliente admitido puede recibir un conflicto de disponibilidad al comprar.

#### Scenario: Primera tanda

- **GIVEN** una fila con más clientes esperando que el tamaño de tanda vigente
- **WHEN** se abre una tanda
- **THEN** los primeros clientes de la fila, en orden de llegada, quedan en estado `ADMITTED` con su token y los siguientes siguen en `WAITING`

#### Scenario: Tanda incompleta

- **GIVEN** una fila con menos clientes esperando que el tamaño de tanda vigente
- **WHEN** se abre una tanda
- **THEN** todos los clientes en espera quedan en estado `ADMITTED` con su token

#### Scenario: Orden estable entre instancias

- **GIVEN** dos o más instancias del servicio de la fila atendiendo la misma fila
- **WHEN** se abre una tanda
- **THEN** cada cliente es admitido a lo sumo una vez y el orden de admisión respeta el orden de llegada

#### Scenario: El token de admisión no garantiza entradas

- **GIVEN** un cliente admitido cuyo evento ya no tiene lugares disponibles
- **WHEN** el cliente intenta comprar con su token de admisión
- **THEN** la compra se rechaza por falta de disponibilidad y el token de admisión no produce una reserva

### Requirement: Emitir un token de admisión de un solo uso

La fila SHALL emitir, al admitir a un cliente, un token de admisión ligado al evento y al cliente, con una vigencia acotada y de un solo uso. La fila SHALL NOT emitir el token antes de admitir al turno y SHALL NOT admitir dos turnos con el mismo cliente.

#### Scenario: Token emitido al admitir

- **WHEN** la fila admite el turno de un cliente
- **THEN** la fila emite un token ligado a ese evento y a ese cliente, con su instante de vencimiento

#### Scenario: Token de otro cliente

- **GIVEN** un token de admisión emitido a un cliente
- **WHEN** otro cliente presenta ese token
- **THEN** la fila responde `403` con `application/problem+json` y no habilita la compra

#### Scenario: Token de otro evento

- **GIVEN** un token de admisión emitido para un evento
- **WHEN** el mismo cliente lo presenta para un evento distinto
- **THEN** la fila responde `403` con `application/problem+json` y no habilita la compra

#### Scenario: Token vencido

- **GIVEN** un token de admisión cuya vigencia ya venció
- **WHEN** su titular lo presenta
- **THEN** la fila responde `403` con `application/problem+json` y no habilita la compra

#### Scenario: Token reutilizado

- **GIVEN** un token de admisión ya utilizado para crear una orden
- **WHEN** su titular lo vuelve a presentar para una compra distinta
- **THEN** la fila responde `403` con `application/problem+json` y no habilita una segunda compra

### Requirement: Abandonar el turno y expirar la admisión

La fila SHALL permitir que un cliente abandone su turno y SHALL cerrar el turno cuando su token de admisión venció sin compra. Un cliente que abandonó o expiró SHALL poder volver a entrar, en cuyo caso la fila le asigna una posición nueva al final de la fila. La fila SHALL NOT reutilizar la posición de un turno cerrado.

#### Scenario: Abandonar el turno

- **GIVEN** un cliente con un turno en estado `WAITING`
- **WHEN** el cliente abandona la fila
- **THEN** la fila marca su turno como cerrado y la posición de ese turno no vuelve a ofrecerse

#### Scenario: Volver a entrar después de abandonar

- **GIVEN** un cliente cuyo turno fue cerrado
- **WHEN** el mismo cliente vuelve a entrar a la fila del evento
- **THEN** la fila le asigna un turno nuevo con una posición posterior a la de los turnos que seguían activos

#### Scenario: La admisión vencida sin compra

- **GIVEN** un cliente admitido cuyo token venció sin comprar
- **WHEN** el cliente consulta su turno
- **THEN** la fila informa su turno como cerrado y expirado, sin token de admisión

### Requirement: Cerrar la fila cuando el evento deja de estar en venta

La fila SHALL cesar de admitir turnos cuando el evento deja de estar en venta, y SHALL responder con un conflicto explícito a los clientes que intenten entrar o comprar en esa fila. La fila SHALL NOT impedir la cancelación ni la suspensión de un evento.

#### Scenario: Evento cancelado con clientes esperando

- **GIVEN** un evento de alta demanda con clientes esperando en su fila
- **WHEN** el evento se cancela
- **THEN** la fila deja de admitir turnos y responde `409` a los clientes que intentan entrar

#### Scenario: Compra de un evento que ya no está en venta

- **GIVEN** un evento de alta demanda que fue cancelado
- **WHEN** un cliente presenta un token de admisión válido de ese evento
- **THEN** la compra se rechaza por evento no comprable

### Requirement: Errores consistentes y trazabilidad de la fila

Las respuestas de error de la fila SHALL usar `application/problem+json` con `type`, `title`, `status`, `detail` e `instance`, y SHALL incluir el `X-Correlation-Id` de la operación. Cada operación de la fila SHALL registrar el evento, el cliente, el estado resultante y el tiempo de procesamiento, sin exponer el token de admisión en los registros.

#### Scenario: Error con correlation ID

- **WHEN** un cliente recibe un error de la fila
- **THEN** la respuesta es `application/problem+json` e incluye el mismo `X-Correlation-Id` que se propagó en la solicitud

#### Scenario: Registro de la operación

- **WHEN** un cliente entra a la fila o consulta su turno
- **THEN** el servicio de la fila registra el evento, el cliente, el estado resultante y el tiempo de procesamiento
