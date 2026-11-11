# Proposal

## Why

La compra ya emite tickets, pero el titular no puede pedir una devolución y un administrador no puede resolverla con reintegro y liberación de inventario. La cancelación existente cierra ventas futuras, pero no procesa tickets vendidos. Esto completa US-F7.1 a US-F7.3 y PDF-F7 para la Entrega 1.

## What Changes

- El titular solicita una devolución hasta 72 horas antes del evento; un administrador la aprueba o rechaza.
- Purchase coordina revocación de ticket, reintegro idempotente y liberación de la unidad vendida, con estado durable y reintentos.
- La cancelación administrativa de un evento inicia el mismo proceso para todas sus ventas, impide ventas nuevas y permite consultar el avance.
- La notificación al comprador queda en US-F11, como indica el roadmap de Entrega 2.

## Capabilities

### New Capabilities

- `ticket-refunds`: solicitudes, decisiones administrativas y procesamiento de devoluciones de tickets vendidos.

### Modified Capabilities

- `event-management`: cancelación administrativa y cierre de ventas con coordinación de reintegros.
- `inventory-and-reservations`: liberación idempotente de unidades vendidas y bloqueo de reservas del evento cancelado.
- `api-gateway`: rutas públicas de solicitudes y decisiones sin publicar comandos internos.

## Impact

Purchase, Ticket, Inventory, Catalog, Payment Mock, Gateway, sus esquemas PostgreSQL, contratos OpenAPI, Postman, Compose y pruebas E2E.
