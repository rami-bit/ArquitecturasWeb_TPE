# Proposal

## Why

TicketYa ya tiene Catalog e Inventory, pero aun no puede emitir la entrada digital
que recibe el comprador al completar una compra. El blueprint asigna la emision,
el QR y el snapshot minimo de la entrada a Ticket & Access, separado de pagos e
inventario.

## What Changes

- Agregar un `ticket-service` independiente con su PostgreSQL y estructura
  `api / application / domain / infrastructure`.
- Exponer un comando interno para emitir entradas despues de que Purchase haya
  confirmado la orden.
- Generar un QR firmado y devolver su payload y una imagen PNG en Base64.
- Persistir unicamente el hash del payload QR, junto con el snapshot historico
  minimo del evento y la referencia a la orden.
- Hacer la emision repetible por `orderId + orderItemId`, sin duplicar entradas.
- Integrar el servicio al Compose incremental y documentar el contrato interno.

## Capabilities

### New Capabilities

- `ticket-issuance`: emision idempotente de entradas QR para compras confirmadas.

### Modified Capabilities

Ninguna. La validacion de acceso, revocacion por reintegro, compras, pagos y
cancelaciones quedan fuera de este cambio.

## Impact

- Nuevo `services/ticket-service` y `ticket_db`.
- Se actualizan el POM agregador, `docker-compose.yml` y README.
- Se agrega `docs/ticket-openapi.yaml` y evidencia de verificacion.
- No se agrega una ruta publica al Gateway: Purchase consume el contrato interno
  y entrega las entradas al cliente cuando se implemente.
