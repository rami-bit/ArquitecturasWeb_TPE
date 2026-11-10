# Design: Event Cancellation and Refund Workflow

## Architectural Context
El flujo involucra la coordinación entre tres microservicios:
[ Catalog Service ] ──(Cancel Event)──> [ Inventory Service ] (Release Seats)
│
└───────────────────────────────> [ Ticket Service ] (Invalidate/Refund Tickets)


## Service Responsibilities

### 1. Catalog Service (`catalog-service`)
- Añadir el estado `CANCELLED` al enum `EventStatus`.
- Exponer `POST /events/{eventId}/cancel` para actualizar el estado del evento.

### 2. Inventory Service (`inventory-service`)
- Exponer `POST /inventory/events/{eventId}/release-all` para marcar todos los `InventoryItem` del evento como `AVAILABLE`.
- Exponer `POST /inventory/items/{itemId}/release` para liberar un asiento individual ante devolución.

### 3. Ticket Service (`ticket-service`)
- Añadir los estados `REFUND_REQUESTED`, `REFUNDED` y `CANCELLED` al enum `TicketStatus`.
- Exponer `POST /tickets/{ticketId}/refund-request` (valida ventana de 72 horas consultando fecha en `catalog-service`).
- Exponer `POST /tickets/{ticketId}/refund-approve` para confirmar la devolución y llamar a `inventory-service` para liberar la plaza.
