# Spec Delta

## Purpose

Purchased tickets connect the existing QR issuance capability to confirmed orders so clients can retrieve the tickets they have actually paid for.

## ADDED Requirements

### Requirement: Issue tickets only for completed orders
Purchase SHALL request Ticket issuance only after payment approval and Inventory confirmation, and SHALL persist returned ticket identifiers against their order items.

#### Scenario: Issue tickets after confirmed purchase
- **WHEN** payment is approved and Inventory confirms the reservation
- **THEN** Purchase requests one idempotent Ticket issuance per order item and returns the resulting tickets in the order view

#### Scenario: Do not issue tickets for rejected purchase
- **WHEN** payment is rejected or the reservation cannot be confirmed
- **THEN** Purchase does not issue tickets

#### Scenario: Retry ticket issuance
- **WHEN** a Ticket call times out after issuance and Purchase retries the same order item
- **THEN** Ticket returns the existing tickets and Purchase does not create duplicates

### Requirement: Protect purchased ticket details
Purchase SHALL expose ticket QR details only to the owner of the paid order.

#### Scenario: Owner retrieves purchased tickets
- **WHEN** the owner inspects a paid order with issued tickets
- **THEN** the response includes the associated ticket identifiers and QR payloads

#### Scenario: Non-owner cannot retrieve tickets
- **WHEN** another client inspects the order
- **THEN** Purchase returns `404` and discloses no ticket or QR data
