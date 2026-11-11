# Spec Delta

## Purpose

Payment Mock provides a locally runnable, observable payment boundary so purchase and refund flows can demonstrate approval, rejection, retries, and compensation without an external provider.

## ADDED Requirements

### Requirement: Process idempotent simulated charges
Payment Mock SHALL persist each charge using an idempotency key and return the same result for an identical retry. Reusing the key with a different amount, currency, order, or result SHALL return `409`.

#### Scenario: Approve a simulated payment
- **WHEN** Purchase submits a valid charge configured to approve
- **THEN** Payment Mock returns a stable payment ID and approved status

#### Scenario: Reject a simulated payment
- **WHEN** Purchase submits a valid charge configured to reject
- **THEN** Payment Mock returns a stable payment ID and rejected status without charging

#### Scenario: Retry an existing charge
- **WHEN** Purchase retries the same charge with the same idempotency key and payload
- **THEN** Payment Mock returns the original payment ID and result

#### Scenario: Reuse a key with a different charge
- **WHEN** a charge key is reused with different payment details
- **THEN** Payment Mock responds `409` and does not create a second charge

### Requirement: Process idempotent refunds
Payment Mock SHALL accept a refund for an approved payment, persist its result, and return the same result on retries.

#### Scenario: Refund an approved payment
- **WHEN** Purchase requests a refund for an approved payment
- **THEN** Payment Mock records one refund and returns its stable identifier

#### Scenario: Reject refund for a declined payment
- **WHEN** Purchase requests a refund for a payment that was not approved
- **THEN** Payment Mock responds `409` and records no refund

### Requirement: Keep payment mock internal and traceable
Payment Mock SHALL be reachable only inside the Compose network and SHALL return structured errors and propagate `X-Correlation-Id`.

#### Scenario: External client cannot call Payment Mock
- **WHEN** a client attempts to access a payment internal route through API Gateway
- **THEN** the Gateway does not route the request to Payment Mock

#### Scenario: Propagate payment correlation ID
- **WHEN** Purchase calls Payment Mock with a correlation ID
- **THEN** Payment Mock includes that value in its response and error details
