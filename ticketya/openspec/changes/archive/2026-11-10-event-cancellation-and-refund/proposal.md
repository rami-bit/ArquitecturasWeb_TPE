# Proposal: Event Cancellation and Ticket Refund Workflow

## Problem Statement
El sistema actual permite la comercialización y reserva de tickets, pero carece de un flujo de cancelación de eventos por parte del organizador y de solicitudes de devolución por parte de los compradores. Esto impide liberar inventario y procesar reintegros de forma consistente entre los microservicios.

## Proposed Solution
1. **Cancelación masiva por evento**: Permitir que `catalog-service` marque un evento como `CANCELLED`, desencadenando la liberación de asientos en `inventory-service` y la invalidación masiva de tickets emitidos en `ticket-service`.
2. **Devolución individual por usuario**: Permitir a los compradores solicitar la devolución de un ticket hasta 72 horas antes del inicio del evento.
3. **Liberación de inventario**: Sincronizar el estado de los asientos en `inventory-service` a `AVAILABLE` cuando se apruebe una devolución o se cancele un evento.
