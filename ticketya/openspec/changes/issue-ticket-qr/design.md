# Design

## Context

El blueprint `establish-ticketya-architecture` asigna a Ticket & Access el
ownership del ticket, su QR y un snapshot minimo del evento. Purchase orquesta la
compra y llama a Ticket despues de confirmar pago e inventario. Purchase todavia
no esta implementado; este cambio deja su contrato interno implementado y
verificable, sin simular ni crear un servicio de compra.

## Goals / Non-Goals

**Goals**

- Emitir una entrada por unidad comprada, usando un snapshot provisto por Purchase.
- Persistir cada entrada y el snapshot en una base exclusiva del servicio.
- Proteger reintentos con unicidad e idempotencia por `orderId + orderItemId`.
- Entregar un payload QR firmado y una representacion PNG.
- No guardar el payload QR ni el precio del evento en la base.

**Non-Goals**

- Crear ordenes, procesar pagos o reservar inventario.
- Verificar por llamada propia que una orden fue pagada; el comando interno es
  invocado por Purchase unicamente despues de confirmar la compra.
- Exponer datos de entrada a clientes directamente ni agregar rutas al Gateway.
- Validar el acceso, consumir el QR, revocar entradas o procesar devoluciones.
- Agregar identidad/JWT, broker o cache.

## Decisions

### Servicio y contrato

`ticket-service` escucha en el puerto interno 8083. Purchase invocara
`POST /internal/orders/{orderId}/items/{orderItemId}/tickets` una vez por item de
orden confirmado. La solicitud trae el comprador, el snapshot minimo del evento
y una lista de unidades (asiento numerado o admision general). No incluye precio.

La respuesta es `201` en la primera emision y `200` al repetir una solicitud
identica. Reutilizar la misma pareja de IDs con un snapshot o unidades distintos
responde `409`; nunca se reemplazan entradas ya emitidas.

El endpoint es interno y no se enruta por el Gateway. En E1 la frontera de red de
Compose es la proteccion prevista para llamadas entre servicios. El Gateway sigue
siendo la unica entrada de clientes; Purchase devolvera al cliente las entradas
recibidas de Ticket.

### Persistencia e idempotencia

`ticket_db` es propiedad exclusiva de Ticket. `orderId`, `orderItemId`,
`eventId`, `customerId` y el resto de referencias remotas son valores, no foreign
keys hacia otras bases.

`ticket_issuances` registra el fingerprint canonico por
`(order_id, order_item_id)`. `tickets` guarda una fila por admision, con
`(issuance_id, unit_number)` unico. Un insert PostgreSQL `ON CONFLICT DO NOTHING`
serializa reintentos concurrentes; el servicio compara fingerprint antes de
devolver entradas existentes. Una solicitud distinta con la misma clave produce
conflicto sin mutar el estado.

La transaccion crea el registro de emision y todas sus entradas. Un fallo parcial
revierte todo el item.

### QR y datos almacenados

El QR contiene un token versionado firmado con HMAC-SHA-256 que identifica el
ticket. La clave se recibe por `TICKET_QR_SIGNING_SECRET`; no se versiona ninguna
clave. La estabilidad de la clave es necesaria para que un reintento produzca el
mismo QR. El PNG se genera al responder mediante ZXing y no se persiste.

La base solo almacena SHA-256 del token (`qr_hash`), con unicidad. El snapshot
histórico incluye nombre y fecha del evento, lugar, ciudad, sector y asiento si
corresponde; no contiene precio ni payload QR. Cambiar la clave invalida los QR
emitidos, por lo que su rotacion queda fuera de este incremento.

### Estructura y verificacion

El codigo sigue el patron del equipo: `api` para controllers/DTOs, `application`
para casos de uso, `domain` para entidades/estados, `infrastructure/persistence`
para repositorios. El servicio tiene Dockerfile y PostgreSQL propios y se integra
al Compose raiz sin publicar puertos.

Las pruebas verifican validacion HTTP, emision PNG, firma estable, hash persistido
sin token en claro, idempotencia, conflicto por contenido diferente y unicidad
con solicitudes concurrentes. La integracion Purchase -> Ticket queda pendiente
hasta que exista Purchase Service.

## Risks / Trade-offs

- Si Purchase no proporciona un snapshot correcto de la orden confirmada, Ticket
  conserva datos historicos incorrectos. Purchase es el unico caller autorizado
  por contrato y debe enviar el snapshot de la orden.
- La clave HMAC debe ser estable entre instancias y reinicios; no debe exponerse
  en logs ni respuestas.
- Este incremento emite entradas, pero no puede validar acceso ni gestionar
  devoluciones hasta completar las capacidades relacionadas.
