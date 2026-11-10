# Implementation Tasks

- [ ] 1. **Catalog Service**: Extender `EventStatus` con `CANCELLED` y crear controlador `POST /events/{eventId}/cancel`.
- [ ] 2. **Inventory Service**: Agregar método en `ReservationService` y endpoint `POST /inventory/events/{eventId}/release-all` para resetear el estado de los asientos a `AVAILABLE`.
- [ ] 3. **Ticket Service**: Extender `TicketStatus` con los estados de devolución (`REFUND_REQUESTED`, `REFUNDED`, `CANCELLED`).
- [ ] 4. **Ticket Service**: Implementar `POST /tickets/{ticketId}/refund-request` validando la restricción de 72hs de anticipación.
- [ ] 5. **Ticket Service**: Implementar `POST /tickets/{ticketId}/refund-approve` que invoque a `inventory-service` para restaurar disponibilidad.
- [ ] 6. **API Gateway**: Registrar y verificar las nuevas rutas en `application.yml` de `api-gateway`.
