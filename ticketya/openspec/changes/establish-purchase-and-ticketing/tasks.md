# Tasks

## 1. Purchase Service and order API
- [ ] 1.1 Add Purchase Spring Boot module, application config, database schema, Dockerfile, and root Maven aggregation.
- [ ] 1.2 Implement order creation, catalog price snapshots, identity ownership, request fingerprint idempotency, and atomic per-client/event limit.
- [ ] 1.3 Implement order lookup, problem responses, correlation propagation, OpenAPI, and unit/persistence tests.

## 2. Payment Mock
- [ ] 2.1 Add Payment Mock module, owned PostgreSQL schema, Dockerfile, configuration, and health endpoint.
- [ ] 2.2 Implement idempotent simulated charges and refunds with stable outcomes and conflict detection.
- [ ] 2.3 Add unit and persistence tests and document its internal contract.

## 3. Purchase orchestration and ticket integration
- [ ] 3.0 Align Inventory availability and reservation sector IDs with Catalog source sector IDs.
- [ ] 3.1 Implement persisted purchase transitions calling Inventory hold/confirm/release and Payment charge/refund contracts.
- [ ] 3.2 Integrate idempotent Ticket issuance after successful payment and inventory confirmation; return tickets from order lookup.
- [ ] 3.3 Cover approval, rejection, duplicate request, limit/concurrency, expiry, downstream failure, and ticket retry behavior with tests.

## 4. Gateway and local integration
- [ ] 4.1 Route public purchase operations through Gateway and preserve identity, correlation, and problem responses.
- [ ] 4.2 Add Purchase and Payment databases/services to root Compose with healthchecks and internal-only ports.
- [ ] 4.3 Add Postman requests, README instructions and sample flows for approved and rejected purchases.
- [ ] 4.4 Verify strict OpenSpec validation, Maven verification, Compose health, and an end-to-end purchase through Gateway.
