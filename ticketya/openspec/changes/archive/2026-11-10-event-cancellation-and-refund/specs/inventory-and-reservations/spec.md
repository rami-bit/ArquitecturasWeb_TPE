# Inventory and Reservations Delta

## Requirement: Inventory Release on Refund or Cancellation

### Scenario: Release Single Seat on Refund
- **Given** an `InventoryItem` with status `SOLD`.
- **When** a refund approval is processed for that seat.
- **Then** the `InventoryItem` status transitions back to `AVAILABLE`.
- **And** the sector available capacity increases by 1.
