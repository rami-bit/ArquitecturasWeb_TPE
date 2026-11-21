# Documento de diseño y backlog funcional — TicketYa

> Este documento transforma los requerimientos funcionales del PDF en features y user stories trazables. Las historias **E1** describen capacidades implementadas en la primera entrega según el código y las especificaciones vigentes; las historias **E2** son únicamente el alcance pendiente indicado por el PDF. La inclusión de una historia E2 no implica implementación ni forma parte de esta tarea.

## 1. Features y user stories

### Feature F1 — Datos y gestión de eventos (Entrega 1)

#### US-E1-01 — Crear y configurar un evento en borrador

**Como** organizador, **quiero** crear y configurar un evento propio, **para** prepararlo antes de publicarlo.

**Trazabilidad:** PDF-F1. **Estado:** implementado en E1.

**Criterios de aceptación**

- **Dado** un organizador y un lugar existente, **cuando** crea un evento, **entonces** el sistema le asigna un identificador, lo deja inicialmente en `DRAFT` y lo asocia a ese organizador.
- **Dado** un evento en `DRAFT`, **cuando** su organizador configura los datos del evento, **entonces** puede definir nombre, descripción, tipo, participantes, fecha y hora, lugar y sectores con sus precios.
- **Dado** un evento que ya no está en `DRAFT`, **cuando** se intenta modificar su configuración, **entonces** se rechaza el cambio y se conserva el snapshot publicado.

### Feature F2 — Lugares, sectores y asientos (Entrega 1)

#### US-E1-02 — Administrar lugares y su configuración física

**Como** administrador, **quiero** registrar lugares y configurar sus sectores, **para** que puedan usarse en eventos.

**Trazabilidad:** PDF-F2. **Estado:** implementado en E1.

**Criterios de aceptación**

- **Dado** un administrador con datos válidos, **cuando** registra un lugar, **entonces** queda identificado con nombre, dirección, ciudad y ubicación geográfica.
- **Dado** un lugar, **cuando** el administrador configura un sector numerado, **entonces** se registran sus filas y asientos identificables y no se aceptan duplicados dentro del sector.
- **Dado** un lugar, **cuando** el administrador configura un sector no numerado, **entonces** se registra su capacidad máxima positiva.
- **Dado** un lugar utilizado por un evento publicado, **cuando** se modifica la plantilla del lugar, **entonces** el snapshot de ese evento no cambia.

### Feature F3 — Publicación y visibilidad de eventos (Entrega 1)

#### US-E1-03 — Publicar un evento configurado

**Como** organizador, **quiero** publicar un borrador válido, **para** que los clientes puedan consultarlo.

**Trazabilidad:** PDF-F3. **Estado:** implementado en E1.

**Criterios de aceptación**

- **Dado** un borrador con fecha futura, participantes y al menos un sector habilitado con precio positivo, **cuando** el organizador solicita publicarlo, **entonces** pasa a `PUBLISHED` y se conserva un snapshot de su configuración.
- **Dado** un evento, **cuando** se consulta su estado, **entonces** el modelo reconoce `DRAFT`, `PUBLISHED`, `SOLD_OUT`, `CANCELLED` y `FINISHED`, además de `SUSPENDED`; las transiciones de `SOLD_OUT` y `FINISHED` no forman parte de los flujos implementados en E1.
- **Dado** un borrador incompleto o con fecha no futura, **cuando** se solicita publicarlo, **entonces** se rechaza la publicación y permanece en `DRAFT`.
- **Dado** un evento publicado, **cuando** un administrador lo suspende, **entonces** deja de aparecer en las búsquedas públicas; un evento suspendido puede ser reanudado por un administrador.
- **Dado** un evento ya publicado, **cuando** se repite la publicación, **entonces** se conserva la publicación existente sin duplicar el snapshot.

### Feature F4 — Compra, reserva temporal y entradas (Entrega 1)

#### US-E1-04 — Completar una compra idempotente con límite por cliente

**Como** cliente, **quiero** comprar asientos numerados o cupos de admisión general, **para** obtener entradas para un evento publicado.

**Trazabilidad:** PDF-F4. **Estado:** implementado en E1.

**Criterios de aceptación**

- **Dado** un evento publicado y unidades disponibles, **cuando** el cliente solicita una compra válida, **entonces** el sistema obtiene los precios del snapshot del evento, reserva los lugares antes del cobro simulado y registra el detalle de la compra y su medio de pago.
- **Dada** una reserva creada sin un TTL configurado para el evento, **cuando** se retienen temporalmente sus lugares, **entonces** vence a los 600 segundos y, si no se confirma, los lugares se liberan.
- **Dado** un cliente que repite la misma compra con la misma `Idempotency-Key` y contenido, **cuando** reintenta la solicitud, **entonces** recibe la orden existente sin reservas, cobros ni entradas duplicadas. Si reutiliza la clave con otro contenido, la solicitud se rechaza como conflicto.
- **Dado** un cliente que alcanza el límite de entradas de un evento, **cuando** intenta superarlo, **entonces** la compra se rechaza antes de reservar o cobrar. El límite es configurable, seis entradas por defecto, y su control incluye órdenes pendientes y confirmadas.
- **Dado** un pago rechazado o una reserva vencida, **cuando** el sistema resuelve la compra, **entonces** libera los lugares y no confirma entradas; las compras rechazadas o compensadas liberan las unidades computadas para el límite.
- **Dado** un pago cuyo resultado queda pendiente, **cuando** el cliente consulta/reconcilia la compra, **entonces** el sistema conserva la reserva hasta su vencimiento y puede continuar la operación sin volver a cobrar.

#### US-E1-05 — Emitir entradas con QR único

**Como** cliente, **quiero** recibir una entrada digital por cada unidad comprada, **para** contar con una entrada verificable para el evento.

**Trazabilidad:** PDF-F4. **Estado:** implementado en E1.

**Criterios de aceptación**

- **Dado** un pago aprobado y una reserva confirmada, **cuando** termina la emisión, **entonces** se entrega una entrada con QR firmado por cada unidad comprada, tanto numerada como de admisión general.
- **Dado** un ítem de compra cuya emisión ya se realizó, **cuando** se reintenta la misma emisión, **entonces** se devuelven las mismas entradas y QR, sin crear entradas duplicadas.
- **Dado** un QR emitido, **cuando** Ticket Service lo persiste, **entonces** `ticket_db` conserva sólo el hash SHA-256 del payload y no el token en claro. Purchase conserva `qr_payload` y `qr_png_base64` en `purchase_db` para devolver la entrada al titular de la orden.

### Feature F5 — Prevención de la doble venta (Entrega 1)

#### US-E1-06 — Garantizar una única reserva/venta ante concurrencia

**Como** cliente, **quiero** que el sistema proteja la disponibilidad al comprar, **para** que una entrada o el último cupo no se venda a más de una persona.

**Trazabilidad:** PDF-F5. **Estado:** implementado en E1.

**Criterios de aceptación**

- **Dado** un asiento disponible, **cuando** varios clientes intentan reservarlo concurrentemente, **entonces** exactamente una solicitud obtiene la reserva y las restantes no pueden comprarlo.
- **Dado** un único cupo disponible en admisión general, **cuando** varias solicitudes concurrentes compiten por él, **entonces** exactamente una reserva lo consume.
- **Dado** que las solicitudes se atienden desde más de una instancia, **cuando** compiten por el mismo asiento o cupo, **entonces** se mantiene el mismo resultado de exclusividad.
- **Dado** un reintento idéntico de una operación ya confirmada, **cuando** vuelve a procesarse, **entonces** no crea una reserva ni una venta adicional.

### Feature F6 — Fila virtual para eventos de alta demanda (Entrega 2)

#### US-E2-02 — Esperar turno para comprar

**Como** cliente, **quiero** conocer mi posición en la fila de un evento de alta demanda, **para** saber cuándo puedo iniciar la compra.

**Trazabilidad:** PDF-F6. **Estado:** implementado en E2 (`add-virtual-queue`).

**Criterios de aceptación**

- **Dado** un evento publicado y marcado como de alta demanda, **cuando** un cliente ingresa a la fila virtual, **entonces** recibe un turno con su posición, calculada por un contador por evento que nunca repite un número.
- **Dado** un cliente que ya tiene un turno activo en la fila, **cuando** vuelve a ingresar, **entonces** obtiene el mismo turno y la misma posición, sin consumir un lugar adicional.
- **Dado** que hay clientes esperando, **cuando** el sistema habilita el acceso a compra, **entonces** los habilita en orden de llegada y por tandas de tamaño fijo, sin admitir a nadie con una posición posterior mientras haya turnos anteriores en espera.
- **Dado** un turno admitido, **cuando** el cliente consulta su posición, **entonces** el sistema le entrega un token de admisión de vigencia corta, ligado al evento y al comprador, y de un solo uso.
- **Dado** un token vencido, ajeno, de otro evento o ya consumido, **cuando** se intenta comprar un evento de alta demanda, **entonces** la compra se rechaza sin reservar stock ni cobrar.
- **Dado** un cliente con un turno admitido, **cuando** inicia la compra dentro de la vigencia del token y hay disponibilidad, **entonces** la orden se crea y el token queda consumido; un reintento con la misma `Idempotency-Key` no consume un segundo token.
- **Dado** un cliente que abandona la fila o cuyo turno venció, **cuando** vuelve a ingresar, **entonces** recibe una posición nueva al final de la fila.
- **Dado** un evento publicado sin la marca de alta demanda, **cuando** un cliente intenta ingresar a la fila, **entonces** el sistema responde que el evento no tiene fila y la compra sigue sin token de admisión.
- **Dado** un evento cancelado o despublicado, **cuando** un cliente consulta su turno, **entonces** la fila se cierra y no admite nuevas compras.
- La fila **no** informa la disponibilidad de entradas ni reserva inventario: Inventory sigue siendo la única autoridad de stock, y una caída de la fila no bloquea la cancelación del evento.

### Feature F7 — Devoluciones y cancelación de eventos (Entrega 1)

#### US-E1-07 — Solicitar y resolver la devolución de una entrada

**Como** titular de una entrada, **quiero** solicitar su devolución y recibir una decisión administrativa, **para** obtener el reintegro cuando corresponda.

**Trazabilidad:** PDF-F7. **Estado:** implementado en E1.

**Criterios de aceptación**

- **Dado** un ticket pagado propio y un evento que comienza en más de 72 horas, **cuando** su titular solicita la devolución, **entonces** se crea una solicitud pendiente; un reintento devuelve la misma solicitud.
- **Dado** que faltan 72 horas o menos para el evento, **cuando** se solicita la devolución, **entonces** se rechaza sin alterar la entrada ni crear una solicitud.
- **Dada** una solicitud pendiente, **cuando** un administrador la rechaza, **entonces** la entrada conserva su validez y el lugar permanece vendido.
- **Dada** una solicitud pendiente, **cuando** un administrador la aprueba, **entonces** se procesa el reintegro, se revoca la entrada y se libera el lugar vendido; los pasos pueden reintentarse sin duplicar efectos.

#### US-E1-08 — Cancelar un evento y procesar sus reintegros

**Como** administrador, **quiero** cancelar un evento y procesar las entradas ya vendidas, **para** cerrar la venta y reintegrar a sus compradores.

**Trazabilidad:** PDF-F7 (y PDF-F3 para la cancelación administrativa). **Estado:** implementado en E1.

**Criterios de aceptación**

- **Dado** un evento cancelable con entradas vendidas, **cuando** un administrador inicia la cancelación, **entonces** el evento queda `CANCELLED`, se bloquean nuevas reservas y se procesan las entradas vendidas para revocación, reintegro y liberación del lugar.
- **Dado** un lote de reintegros que no terminó, **cuando** se reintenta su procesamiento, **entonces** se informa el progreso y no se duplican reintegros ni liberaciones ya completados.
- **Dado** un evento cancelado, **cuando** se repite el procesamiento, **entonces** se conserva una única operación por entrada y se completa el trabajo pendiente.
- La notificación a compradores por una cancelación pertenece a PDF-F11 y queda fuera de E1.

### Feature F8 — Control de acceso mediante QR (Entrega 2)

#### US-E2-03 — Validar una entrada en el acceso

**Como** personal de acceso, **quiero** escanear y validar el QR de una entrada, **para** permitir el ingreso una sola vez al evento correspondiente.

**Trazabilidad:** PDF-F8, PDF-NF-SECURITY, PDF-NF-TRACE. **Estado:** **IMPLEMENTADO** (épica E2-03, cambio OpenSpec `validate-ticket-access`).

**Criterios de aceptación**

- **Dada** una entrada existente, **cuando** se escanea para el evento al que corresponde y todavía no fue utilizada, **entonces** el sistema la valida y la marca como utilizada.
- **Dado** un QR inexistente, de otro evento o ya utilizado, **cuando** se intenta validarlo, **entonces** el sistema no autoriza el ingreso.
- **Dado** el mismo QR escaneado nuevamente, **cuando** se intenta ingresar por segunda vez, **entonces** se rechaza el ingreso.
- **Dado** un QR con la firma alterada, **cuando** se escanea, **entonces** se rechaza como `UNKNOWN_TICKET` sin modificar ninguna entrada.
- **Dada** una entrada revocada por devolución o cancelación, **cuando** se escanea, **entonces** se rechaza como `REVOKED`.
- **Dada** una entrada de otro evento, **cuando** se escanea, **entonces** se rechaza como `WRONG_EVENT` y la entrada no se consume.
- **Dados** varios escaneos simultáneos del mismo QR (aun en distintas instancias), **cuando** se procesan, **entonces** exactamente uno ingresa y el resto recibe `ALREADY_USED`.
- **Dado** un usuario sin rol `ACCESS_STAFF`, **cuando** intenta validar un QR, **entonces** recibe `403` y la entrada no cambia.
- **Dado** un `ACCESS_STAFF` o `ADMIN`, **cuando** consulta los intentos de ingreso de un evento, **entonces** obtiene cada intento con su resultado y los totales por resultado.

### Feature F9 — Búsqueda y ordenamiento de eventos (Entrega 1)

#### US-E1-09 — Buscar eventos con varios criterios

**Como** cliente, **quiero** filtrar y ordenar eventos publicados, **para** encontrar eventos de mi interés.

**Trazabilidad:** PDF-F9. **Estado:** implementado en E1.

**Criterios de aceptación**

- **Dado** que existen eventos publicados, **cuando** el cliente combina filtros de tipo, organizador, participante, ciudad o rango de fechas, **entonces** recibe únicamente los eventos que cumplen todos los filtros indicados.
- **Dado** un rango de fechas válido, **cuando** se busca, **entonces** se incluyen los eventos dentro del rango; si el inicio es posterior al fin, la consulta se rechaza.
- **Dada** una búsqueda, **cuando** se solicita ordenar por fecha, precio o relevancia, **entonces** los resultados se ordenan por ese criterio; la relevancia requiere texto de búsqueda.
- **Dada** una búsqueda pública, **cuando** existen eventos en distintos estados, **entonces** sólo se muestran los eventos `PUBLISHED`.

### Feature F10 — Preferencias y recomendaciones (Entrega 2)

#### US-F10 - Registrar preferencias y recibir recomendaciones

**Como** cliente, **quiero** registrar mis preferencias y recibir sugerencias de eventos, **para** descubrir opciones afines a mis intereses.

**Trazabilidad:** PDF-F10. **Estado:** **IMPLEMENTADO** (`preferences-service` + MongoDB 7; cambio OpenSpec archivado `2026-11-20-add-preferences-and-recommendations`).

**Criterios de aceptación (vigentes, actualizados en Entrega 2)**

- **Dado** un cliente autenticado con rol `CLIENT`, **cuando** registra preferencias, **entonces** el sistema contempla tipos de evento, artistas favoritos y ciudad, deriva la identidad del `sub` del JWT y persiste un documento por cliente en MongoDB.
- **Dado** un cliente con preferencias o historial de compras, **cuando** consulta sugerencias, **entonces** recibe eventos publicados y futuros ordenados por un puntaje explicable con motivos (`FAVORITE_EVENT_TYPE`, `FAVORITE_ARTIST`, `SAME_CITY`, `CO_PURCHASE`, `UPCOMING_FALLBACK`).
- **Dado** un evento comprado por clientes, **cuando** se generan recomendaciones basadas en historial, **entonces** se incluyen eventos que también compraron clientes que compraron para ese evento, acotados a 200 señales y sin exponer órdenes individuales.
- **Dado** un cliente con una compra confirmada de un evento, **cuando** consulta sugerencias, **entonces** ese evento no aparece aunque tenga afinidad alta.
- **Dado** un cliente sin preferencias ni historial, **cuando** consulta sugerencias, **entonces** recibe los próximos eventos con puntaje cero y motivo `UPCOMING_FALLBACK`.
- **Dado** un fallo de Purchase, **cuando** se consultan recomendaciones, **entonces** la respuesta se entrega con `warnings: ["HISTORY_UNAVAILABLE"]`; si falla Catalog, responde `503`.
- **Dado** un rol distinto de `CLIENT` o una solicitud sin JWT, **cuando** intenta acceder a `/api/customers/**`, **entonces** recibe `403` o `401` respectivamente sin invocar al servicio.

### Feature F11 — Notificaciones al cliente (Entrega 2)

#### US-E2-05 — Recibir avisos relevantes

**Como** cliente, **quiero** recibir notificaciones sobre mis compras y eventos de mi interés, **para** estar informado de cambios relevantes.

**Trazabilidad:** PDF-F11, PDF-NF-RESILIENCE, PDF-NF-SECURITY. **Estado:** **IMPLEMENTADO** (cambio OpenSpec `add-customer-notifications`, `notification-service`).

**Criterios de aceptación (revisados y completados en Entrega 2)**

- **Dado** que una compra se confirma, **cuando** se completa la confirmación, **entonces** se envía una notificación por email o push.
- **Dado** un evento que cambia o se cancela, **cuando** ocurre el cambio, **entonces** se notifica a sus compradores.
- **Dado** un artista favorito de un cliente que publica un evento en su ciudad, **cuando** se publica el evento, **entonces** el cliente recibe una notificación.
- **Dada** una orden que se confirma al reconciliarse o que termina compensada, **cuando** cambia su estado, **entonces** el comprador recibe el aviso correspondiente.
- **Dado** un evento suspendido y luego reanudado, **cuando** ocurre cada transición, **entonces** los compradores reciben un aviso por cada una, sin duplicados ante reintentos.
- **Dado** que el productor reenvía el mismo hecho, **cuando** Notification Service lo recibe, **entonces** responde `202` con `duplicate: true` y no duplica avisos.
- **Dado** que Purchase, Preferences o la pasarela de entrega fallan, **cuando** se procesa la notificación, **entonces** se reintenta con espera exponencial y, agotados los intentos, queda `FAILED`; la compra o el cambio del evento nunca fallan por esto.
- **Dada** una transición de evento rechazada (por ejemplo, reanudar un evento no suspendido), **cuando** se intenta, **entonces** no se notifica.
- **Dado** un cliente, **cuando** consulta `GET /api/notifications/me`, **entonces** sólo ve sus notificaciones con canal y estado; otros roles reciben `403` y sin token `401`.

### Feature F12 — Reportes para organizadores (Entrega 2)

#### US-E2-06 — Consultar resultados de un evento

**Como** organizador, **quiero** consultar reportes de mis eventos, **para** conocer sus ventas y resultados.

**Trazabilidad:** PDF-F12, PDF-NF-SECURITY. **Estado:** **IMPLEMENTADO** (épica E2-04, cambio OpenSpec `add-organizer-event-reports`).

**Criterios de aceptación**

- **Dado** un evento del organizador, **cuando** consulta su reporte, **entonces** puede conocer las entradas vendidas y devueltas por sector.
- **Dado** un evento con ventas, **cuando** consulta su reporte, **entonces** puede conocer el porcentaje de ocupación y la recaudación.
- **Dado** un evento de otro organizador, **cuando** un `ORGANIZER` consulta su reporte, **entonces** recibe `403`; un `ADMIN` puede consultar cualquier evento.
- **Dadas** órdenes rechazadas, compensadas o con pago pendiente, **cuando** se arma el reporte, **entonces** no cuentan como vendidas.
- **Dada** una entrada devuelta por el cliente y luego alcanzada por la cancelación del evento, **cuando** se arma el reporte, **entonces** cuenta una sola vez como devuelta.
- **Dado** que Inventory no responde, **cuando** se consulta el reporte, **entonces** se informan ventas y recaudación, y la capacidad y la ocupación quedan nulas con una advertencia.

### Requisito no funcional — Trazabilidad y bitácora centralizada (Entrega 2)

#### US-E2-06 — Seguir una operación crítica a través de los servicios

**Como** administrador, **quiero** que cada solicitud HTTP y cada operación crítica queden registradas con un identificador común, **para** diagnosticar y auditar una compra, devolución, cancelación o ingreso de punta a punta.

**Trazabilidad:** PDF-NF-TRACE, PDF-NF-RESILIENCE. **Estado:** **IMPLEMENTADO** (épica E2-02, cambio OpenSpec `add-request-tracing-and-audit-log`).

**Criterios de aceptación**

- **Dada** cualquier solicitud HTTP, **cuando** el Gateway o un servicio la atiende, **entonces** queda una línea de log con método, endpoint, usuario, rol, código de respuesta, duración y `X-Correlation-Id`, incluidos los `401`/`403` del Gateway.
- **Dada** una compra, devolución, cancelación o validación de ingreso, **cuando** termina (con éxito o con error), **entonces** el servicio productor registra un evento en la bitácora centralizada con el mismo `X-Correlation-Id` de la solicitud.
- **Dado** un `X-Correlation-Id`, **cuando** un `ADMIN` consulta su traza, **entonces** obtiene en orden cronológico los eventos de todos los servicios que participaron.
- **Dado** que Audit Service no responde, **cuando** ocurre una operación crítica, **entonces** la operación de negocio no falla ni se demora: el evento se reintenta y, si sigue sin poder enviarse, queda en el log local.
- **Dado** un rol distinto de `ADMIN`, **cuando** consulta la bitácora, **entonces** recibe `403`.

## 2. Contratos, arquitectura y documentación vigente

- El contrato HTTP vigente es [`docs/openapi.yaml`](openapi.yaml). Este documento no replica rutas, payloads, ejemplos ni códigos de respuesta; deben consultarse en OpenAPI.
- El API Gateway es el punto de entrada de las operaciones externas.
- La arquitectura, responsabilidades de servicios, secuencia de compra y modelos de datos actuales están en [`docs/architecture.md`](architecture.md).
- Los requisitos para configurar, ejecutar y probar el sistema están en [`README.md`](../README.md).
- Las imágenes del borrador inicial no se conservan aquí porque representan un modelo o flujo anterior. Las referencias de arquitectura vigentes son las de `docs/architecture.md`; OpenAPI es la fuente de verdad del contrato.
