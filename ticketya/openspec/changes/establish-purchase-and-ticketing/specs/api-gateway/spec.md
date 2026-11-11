# Spec Delta

## ADDED Requirements

### Requirement: Route public purchases through the Gateway
The Gateway SHALL route public purchase and order-inspection paths to Purchase Service while preserving method, path, query, body, identity headers, and correlation ID. It SHALL NOT route internal service commands.

#### Scenario: Create a purchase through Gateway
- **WHEN** a client sends a purchase request under `/api/purchases`
- **THEN** Purchase Service receives the same request and its response is returned to the client

#### Scenario: Inspect an order through Gateway
- **WHEN** a client requests `/api/purchases/{orderId}`
- **THEN** the Gateway routes the request to Purchase Service and preserves the identity and correlation headers

#### Scenario: Keep payment and service commands private
- **WHEN** a client requests an internal Payment, Ticket, or Inventory command through Gateway
- **THEN** the Gateway does not route it to the internal service
