# Spec Delta

## Purpose

Purchase lets a client create and inspect an order while coordinating inventory, simulated payment, and ticket issuance through durable, retryable state transitions.

## ADDED Requirements

### Requirement: Create an idempotent order with price snapshots
Purchase SHALL accept a client, event, selected sectors/seats, and payment method, resolve event and sector prices from Catalog, and persist an immutable price snapshot with a unique order ID. A repeated idempotency key with the same request SHALL return the same order; a different request SHALL return `409`.

#### Scenario: Create an order with current catalog prices
- **WHEN** an identified client submits a valid order for a published event
- **THEN** Purchase records the selected items and prices returned by Catalog with a stable order ID

#### Scenario: Repeat order creation
- **WHEN** the client repeats the same request with the same idempotency key
- **THEN** Purchase returns the original order without creating another reservation or charge

#### Scenario: Reuse key with different items
- **WHEN** the client reuses an idempotency key with a different event, item, or payment method
- **THEN** Purchase responds `409` and preserves the original order

### Requirement: Enforce per-client event ticket limit
Purchase SHALL enforce a configurable maximum number of tickets per client and event atomically. Paid tickets and orders currently being processed SHALL count; rejected, expired, and cancelled orders SHALL NOT count.

#### Scenario: Client remains within event limit
- **WHEN** a client creates an order that keeps the combined counted quantity within the configured limit
- **THEN** Purchase accepts the order

#### Scenario: Client exceeds event limit concurrently
- **WHEN** concurrent orders would make a client exceed the event limit
- **THEN** only orders within the limit proceed and the others receive `409`

### Requirement: Coordinate order payment and inventory
Purchase SHALL persist order progress and coordinate an Inventory hold, a Payment Mock charge, and Inventory confirmation. It SHALL release an active hold after a declined payment or failed/expired confirmation, and SHALL expose the resulting order state for retry and inspection.

#### Scenario: Approved payment completes purchase
- **WHEN** Inventory holds the requested places and Payment approves the charge
- **THEN** Purchase confirms the hold and marks the order paid

#### Scenario: Declined payment releases places
- **WHEN** Payment declines the charge
- **THEN** Purchase releases the hold and marks the order rejected

#### Scenario: Hold expires before confirmation
- **WHEN** Payment approves after the Inventory hold has expired
- **THEN** Purchase does not issue tickets, records a recoverable failure state, and requests an idempotent refund

#### Scenario: Dependency is temporarily unavailable
- **WHEN** an internal service times out during processing
- **THEN** Purchase retains a nonterminal state and allows the same order operation to resume without duplicating effects

### Requirement: Restrict purchase operations to the order owner
Purchase SHALL derive the client identity from `X-User-Id` and SHALL allow clients to create and inspect only their own orders.

#### Scenario: Inspect own order
- **WHEN** a client requests an order identified as their own
- **THEN** Purchase returns its state, item and price snapshots, payment status, and associated tickets when issued

#### Scenario: Inspect another client's order
- **WHEN** a client requests an order owned by another client
- **THEN** Purchase responds `404` without revealing the order

### Requirement: Trace purchase requests and errors
Purchase SHALL propagate or generate `X-Correlation-Id` across internal calls and return errors as `application/problem+json` with the correlation ID.

#### Scenario: Trace a purchase across services
- **WHEN** a purchase request has a non-empty `X-Correlation-Id`
- **THEN** Purchase forwards that value to Catalog, Inventory, Payment, and Ticket and returns it to the client

#### Scenario: Return a domain error
- **WHEN** Purchase rejects an invalid or conflicting purchase
- **THEN** it returns a problem response containing the resolved correlation ID
