# inventory-and-reservations Specification

## Purpose
Gestiona el inventario autoritativo por evento y las reservas temporales que permiten vender entradas sin duplicar asientos ni cupos, incluso con solicitudes concurrentes y múltiples instancias del servicio.

## Requirements

### Requirement: Inicializar inventario desde el snapshot publicado

Inventory SHALL materializar el layout de un evento a partir de su snapshot publicado en Catalog, de forma idempotente y sin copiar precios. La operación SHALL preservar los identificadores de origen de sectores y asientos y SHALL crear una única configuración de inventario por evento.

#### Scenario: Inicializar un evento publicado sin inventario

- **GIVEN** un evento con snapshot publicado en Catalog y sin inventario materializado
- **WHEN** un cliente consulta su disponibilidad o se solicita una reserva para ese evento
- **THEN** Inventory crea el inventario con sus sectores y asientos y responde con la disponibilidad resultante

#### Scenario: Repetir la inicialización

- **GIVEN** un evento cuyo inventario ya fue inicializado
- **WHEN** se vuelve a consultar su disponibilidad o se solicita otra reserva
- **THEN** Inventory reutiliza el inventario existente sin duplicar sectores, asientos ni configuraciones

#### Scenario: Evento sin snapshot publicado

- **GIVEN** un evento inexistente, en borrador o sin snapshot publicado
- **WHEN** se intenta consultar su disponibilidad o reservar en él
- **THEN** Inventory responde `404` como problema y no crea inventario parcial

#### Scenario: Catalog no disponible durante la inicialización

- **GIVEN** un evento sin inventario materializado
- **WHEN** Catalog no responde al solicitar el snapshot
- **THEN** Inventory responde `503` como problema y no deja inventario parcial

#### Scenario: El snapshot publicado no se reescribe

- **GIVEN** un evento con inventario inicializado
- **WHEN** cambia la plantilla canónica del lugar en Catalog
- **THEN** la disponibilidad del evento conserva el mismo layout previamente materializado

#### Scenario: El inventario no contiene precios

- **GIVEN** un evento con inventario inicializado
- **WHEN** se consulta su disponibilidad o se inspeccionan sus datos
- **THEN** Inventory no expone ni persiste precios del evento o de sus sectores

### Requirement: Consultar disponibilidad pública

Inventory SHALL exponer sin autenticación la disponibilidad de un evento por sector, informando capacidad, cantidad vendida, retenida y disponible. En sectores numerados SHALL informar además el estado de cada asiento, y en todos los casos SHALL NOT incluir precios ni datos de compradores.

#### Scenario: Consultar un evento con sectores numerados

- **GIVEN** un evento publicado con un sector numerado y asientos en estados `AVAILABLE`, `HELD` y `SOLD`
- **WHEN** un cliente consulta `GET /api/inventory/events/{eventId}/availability`
- **THEN** Inventory devuelve el sector con su capacidad y el estado de cada asiento, y la cantidad disponible coincide con los asientos que no están retenidos ni vendidos

#### Scenario: Consultar un evento con admisión general

- **GIVEN** un sector de admisión general con capacidad 100, 30 vendidos y 10 retenidos
- **WHEN** un cliente consulta la disponibilidad del evento
- **THEN** Inventory informa capacidad 100, vendidos 30, retenidos 10 y disponibles 60

#### Scenario: Un asiento retenido no está disponible

- **GIVEN** un asiento con una reserva temporal vigente
- **WHEN** un cliente consulta la disponibilidad del evento
- **THEN** el asiento se informa como `HELD` y no se cuenta como disponible

#### Scenario: Una reserva vencida no reduce la disponibilidad

- **GIVEN** una reserva cuyo vencimiento ya pasó y que todavía no fue barrida
- **WHEN** un cliente consulta la disponibilidad del evento
- **THEN** Inventory libera la reserva antes de responder y el asiento o cupo vuelve a figurar como disponible

#### Scenario: Consultar un evento sin inventario posible

- **GIVEN** un evento inexistente o sin snapshot publicado
- **WHEN** un cliente consulta su disponibilidad
- **THEN** Inventory responde `404` con `application/problem+json`

#### Scenario: Consultar sin identidad

- **WHEN** un cliente consulta la disponibilidad sin encabezados de identidad
- **THEN** Inventory procesa la consulta como pública

#### Scenario: No exponer datos sensibles ni precios

- **GIVEN** una disponibilidad con asientos vendidos y retenidos
- **WHEN** un cliente la consulta
- **THEN** la respuesta no incluye precios, identificadores de compradores, `orderId` ni identificadores de reserva

### Requirement: Reservar lugares temporalmente

Inventory SHALL crear una reserva temporal que retenga todos los ítems solicitados en una única transacción, o ninguno. La reserva SHALL tener un identificador, un vencimiento y una referencia al `orderId` del comprador, y los comandos de reserva SHALL ser internos y no publicarse a clientes externos.

#### Scenario: Reservar asientos numerados disponibles

- **GIVEN** dos asientos disponibles del mismo evento
- **WHEN** Purchase solicita una reserva para ambos con un `orderId` nuevo
- **THEN** Inventory crea una reserva, devuelve su identificador y vencimiento, y marca ambos asientos como `HELD`

#### Scenario: Reservar cupos de admisión general

- **GIVEN** un sector de admisión general con cupo suficiente
- **WHEN** Purchase solicita una reserva de varias unidades
- **THEN** Inventory incrementa la cantidad retenida del sector y devuelve la reserva con su vencimiento

#### Scenario: No reservar parcialmente

- **GIVEN** una solicitud de varios asientos donde uno ya está retenido o vendido
- **WHEN** Purchase solicita la reserva
- **THEN** Inventory rechaza la operación con `409`, no retiene ningún asiento y no deja una reserva activa

#### Scenario: Competir por el último cupo de una zona

- **GIVEN** un sector de admisión general con un único cupo disponible
- **WHEN** una solicitud pide dos cupos
- **THEN** Inventory rechaza la reserva con `409` y conserva el cupo disponible

#### Scenario: Repetir la reserva con el mismo orderId

- **GIVEN** una reserva activa creada para un `orderId`
- **WHEN** Purchase repite la misma solicitud con el mismo `orderId` y los mismos ítems
- **THEN** Inventory devuelve la misma reserva sin retener lugares adicionales

#### Scenario: Reutilizar el orderId con otros ítems

- **GIVEN** una reserva creada para un `orderId`
- **WHEN** Purchase repite el `orderId` con ítems diferentes
- **THEN** Inventory rechaza la solicitud con `409` por conflicto de idempotencia

#### Scenario: Solicitud de reserva inválida

- **WHEN** Purchase informa un sector inexistente, un asiento ajeno al sector, una cantidad no positiva o una mezcla inválida de asiento y cantidad
- **THEN** Inventory rechaza la solicitud con `400` sin modificar el inventario

#### Scenario: Recuperar un asiento cuya reserva venció

- **GIVEN** un asiento retenido por una reserva vencida que todavía no fue barrida
- **WHEN** otra compra solicita ese asiento
- **THEN** Inventory libera la reserva vencida y permite la nueva reserva si el asiento sigue disponible

### Requirement: Configurar la retención por evento

Inventory SHALL aplicar una retención predeterminada de 600 segundos a cada evento y SHALL permitir configurarla entre 60 y 3600 segundos mediante un contrato interno. Una reserva SHALL usar el valor vigente del evento salvo que solicite explícitamente un valor dentro del rango.

#### Scenario: Usar la retención predeterminada

- **GIVEN** un evento sin configuración explícita de retención
- **WHEN** se crea una reserva sin solicitar un TTL
- **THEN** su vencimiento se calcula a 600 segundos de la creación

#### Scenario: Configurar la retención de un evento

- **GIVEN** un evento con inventario inicializado
- **WHEN** se configura su retención en 300 segundos
- **THEN** las reservas posteriores sin TTL explícito vencen a los 300 segundos

#### Scenario: Rechazar una retención fuera de rango

- **WHEN** se intenta configurar o solicitar una retención menor que 60 o mayor que 3600 segundos
- **THEN** Inventory rechaza la solicitud con `400` y conserva el valor anterior

### Requirement: Confirmar la venta de una reserva

Inventory SHALL confirmar una reserva activa y no vencida convirtiendo todos sus lugares retenidos en vendidos, de forma atómica e idempotente. Una confirmación repetida SHALL devolver el mismo resultado sin duplicar decrementos ni ventas.

#### Scenario: Confirmar una reserva vigente

- **GIVEN** una reserva activa con asientos retenidos y cupos generales retenidos
- **WHEN** Purchase confirma la reserva antes de su vencimiento
- **THEN** los asientos pasan a `SOLD`, los cupos generales pasan de retenidos a vendidos y la disponibilidad refleja la venta

#### Scenario: Repetir la confirmación

- **GIVEN** una reserva ya confirmada
- **WHEN** Purchase repite la confirmación
- **THEN** Inventory devuelve el mismo resultado sin volver a incrementar vendidos ni cambiar asientos

#### Scenario: Confirmar una reserva vencida

- **GIVEN** una reserva cuyo vencimiento ya pasó
- **WHEN** Purchase intenta confirmarla
- **THEN** Inventory responde `409`, no marca lugares como vendidos y mantiene el inventario disponible

#### Scenario: Confirmar una reserva liberada

- **GIVEN** una reserva liberada
- **WHEN** Purchase intenta confirmarla
- **THEN** Inventory responde `409` y no modifica el inventario

#### Scenario: Confirmación concurrente de la misma reserva

- **GIVEN** una reserva activa
- **WHEN** dos instancias confirman la misma reserva al mismo tiempo
- **THEN** Inventory registra una única venta y ambas respuestas reflejan el estado confirmado

### Requirement: Liberar una reserva

Inventory SHALL liberar una reserva activa y devolver sus lugares a disponible de forma atómica e idempotente. Una liberación repetida SHALL conservar el estado final sin alterar lugares ya vendidos.

#### Scenario: Liberar una reserva activa

- **GIVEN** una reserva activa con asientos y cupos generales
- **WHEN** Purchase la libera
- **THEN** los asientos vuelven a `AVAILABLE`, los cupos generales se descuentan de retenidos y la disponibilidad se incrementa

#### Scenario: Repetir la liberación

- **GIVEN** una reserva ya liberada
- **WHEN** Purchase repite la liberación
- **THEN** Inventory devuelve el estado liberado sin modificar nuevamente el inventario

#### Scenario: Intentar liberar una reserva confirmada

- **GIVEN** una reserva confirmada con lugares vendidos
- **WHEN** Purchase intenta liberarla
- **THEN** Inventory responde `409` y conserva los lugares como vendidos

#### Scenario: Liberar una reserva vencida

- **GIVEN** una reserva vencida cuyos lugares ya fueron liberados por expiración
- **WHEN** Purchase solicita liberarla
- **THEN** Inventory devuelve su estado final sin modificar el inventario

### Requirement: Expirar reservas automáticamente

Inventory SHALL liberar las reservas activas cuyo vencimiento haya pasado sin afectar lugares vendidos. La expiración SHALL ejecutarse antes de calcular disponibilidad o reservar y mediante un barrido periódico, y SHALL ser segura cuando varias instancias la ejecutan a la vez.

#### Scenario: Expiración perezosa antes de consultar

- **GIVEN** una reserva activa cuyo vencimiento ya pasó
- **WHEN** se consulta la disponibilidad del evento
- **THEN** Inventory la marca como expirada, libera sus lugares y responde con la disponibilidad actualizada

#### Scenario: Barrido periódico sin tráfico

- **GIVEN** una reserva activa cuyo vencimiento ya pasó y ningún cliente consulta el evento
- **WHEN** se ejecuta el barrido periódico
- **THEN** Inventory libera la reserva y sus lugares quedan disponibles

#### Scenario: Expiración concurrente entre instancias

- **GIVEN** una reserva vencida
- **WHEN** dos instancias intentan expirarla al mismo tiempo
- **THEN** sólo una transición surte efecto y los contadores de cupos no se descuentan dos veces

#### Scenario: La expiración no toca lugares vendidos

- **GIVEN** asientos y cupos vendidos
- **WHEN** se ejecuta la expiración
- **THEN** Inventory sólo modifica reservas activas vencidas y conserva intactos los lugares vendidos

#### Scenario: Una reserva expirada no puede confirmarse

- **GIVEN** una reserva expirada
- **WHEN** Purchase intenta confirmarla
- **THEN** Inventory responde `409` y no realiza la venta

### Requirement: Evitar la doble venta bajo concurrencia

Inventory SHALL garantizar que, ante solicitudes concurrentes sobre el mismo asiento o el último cupo de una zona, sólo una reserva o confirmación tenga éxito, incluso con más de una instancia del servicio. La garantía SHALL residir en PostgreSQL y SHALL NOT depender de memoria, cache ni una lectura previa.

#### Scenario: Muchos clientes compiten por el mismo asiento

- **GIVEN** un asiento disponible
- **WHEN** muchas solicitudes concurrentes intentan reservarlo
- **THEN** exactamente una reserva lo obtiene y las demás reciben `409`

#### Scenario: Muchos clientes compiten por el último cupo

- **GIVEN** un sector de admisión general con un único cupo disponible
- **WHEN** muchas solicitudes concurrentes intentan reservar ese cupo
- **THEN** exactamente una reserva tiene éxito y el resto recibe `409`

#### Scenario: Dos instancias compiten por el mismo asiento

- **GIVEN** dos instancias de Inventory conectadas a la misma `inventory_db`
- **WHEN** ambas intentan reservar o confirmar el mismo asiento al mismo tiempo
- **THEN** PostgreSQL permite una única transición exitosa y la otra operación es rechazada o resuelta idempotentemente

#### Scenario: Reintento tras una conexión perdida

- **GIVEN** una reserva ya creada para un `orderId`
- **WHEN** Purchase reintenta la misma reserva por una conexión perdida
- **THEN** Inventory devuelve la reserva existente y no crea una segunda retención ni una segunda venta

### Requirement: Idempotencia de los comandos internos

Inventory SHALL persistir la clave y el resultado de cada comando interno para devolver el mismo resultado ante reintentos. Una clave reutilizada con un contenido distinto SHALL rechazarse como conflicto sin modificar el inventario.

#### Scenario: Reintentar una reserva idéntica

- **GIVEN** una reserva creada con un `orderId` y un conjunto de ítems
- **WHEN** se repite exactamente la misma solicitud
- **THEN** Inventory devuelve la misma reserva y el mismo vencimiento

#### Scenario: Reintentar una confirmación idéntica

- **GIVEN** una reserva confirmada
- **WHEN** se repite la confirmación con el mismo identificador
- **THEN** Inventory devuelve el mismo estado confirmado

#### Scenario: Reintentar una liberación idéntica

- **GIVEN** una reserva liberada
- **WHEN** se repite la liberación con el mismo identificador
- **THEN** Inventory devuelve el mismo estado liberado

#### Scenario: Clave reutilizada con contenido distinto

- **GIVEN** una reserva creada para un `orderId`
- **WHEN** se repite el comando con el mismo `orderId` pero otros ítems
- **THEN** Inventory responde `409` y conserva la reserva original

### Requirement: Errores consistentes y correlation ID

Inventory SHALL responder los errores con `application/problem+json` incluyendo al menos `status`, `title`, `detail`, `instance` y `correlationId`. SHALL conservar un `X-Correlation-Id` entrante no vacío o generar uno cuando falte, y SHALL devolverlo en todas las respuestas.

#### Scenario: Propagar un correlation ID suministrado

- **WHEN** una solicitud llega con `X-Correlation-Id: trace-inventory-1`
- **THEN** Inventory usa ese valor en sus logs y lo devuelve en la respuesta

#### Scenario: Generar un correlation ID ausente

- **WHEN** una solicitud llega sin `X-Correlation-Id`
- **THEN** Inventory genera un identificador y lo devuelve en la respuesta

#### Scenario: Error de disponibilidad

- **WHEN** Inventory responde `404`, `409`, `400` o `503`
- **THEN** el cuerpo es `application/problem+json` con los campos requeridos y el `correlationId` de la solicitud

#### Scenario: No exponer detalles internos

- **WHEN** ocurre un error inesperado
- **THEN** Inventory responde un problema genérico sin trazas, credenciales ni detalles de la base de datos

### Requirement: Ownership exclusivo del inventario

Inventory SHALL ser el único dueño de `inventory_db` y SHALL NOT acceder directamente a las tablas de Catalog ni persistir precios autoritativos. Las referencias a eventos, sectores y asientos SHALL validarse mediante contratos y conservarse como valores, sin foreign keys entre bases.

#### Scenario: Inicializar sin acceder a catalog_db

- **GIVEN** Catalog Service disponible
- **WHEN** Inventory inicializa un evento
- **THEN** obtiene el layout por un contrato interno y no abre conexiones ni consultas contra `catalog_db`

#### Scenario: Conservar identificadores de origen

- **GIVEN** un snapshot publicado con identificadores de sector y asiento
- **WHEN** Inventory materializa el inventario
- **THEN** conserva esos identificadores para poder correlacionar disponibilidad, reservas y futuras ventas

#### Scenario: No persistir precios

- **GIVEN** un snapshot publicado que incluye precios
- **WHEN** Inventory lo consume
- **THEN** descarta los precios y no los guarda ni los devuelve en su API

### Requirement: Liberar una unidad vendida tras reintegro
Inventory SHALL liberar una unidad vendida identificada por reserva e ítem de manera idempotente y atómica, sin afectar otras unidades de la misma venta.

#### Scenario: Liberar asiento vendido
- **GIVEN** un asiento vendido de una reserva confirmada
- **WHEN** Purchase informa el reintegro confirmado de ese ítem
- **THEN** el asiento pasa a disponible y una repetición no cambia otros contadores

#### Scenario: Liberar cupo general vendido
- **GIVEN** una unidad vendida de admisión general
- **WHEN** Purchase informa su reintegro confirmado
- **THEN** vendidos baja en una unidad y disponible sube en una unidad

#### Scenario: Unidad ajena o no vendida
- **WHEN** Purchase intenta liberar una unidad que no pertenece a la reserva confirmada o ya no está vendida por otra causa
- **THEN** Inventory responde conflicto sin alterar disponibilidad

### Requirement: Bloquear reservas tras cancelación
Inventory SHALL rechazar nuevas reservas para un evento cancelado sin alterar reservas confirmadas hasta recibir comandos de liberación por unidad.

#### Scenario: Evento cancelado
- **WHEN** Purchase intenta reservar una unidad de un evento cancelado
- **THEN** Inventory rechaza la reserva y conserva los lugares vendidos hasta su reintegro
