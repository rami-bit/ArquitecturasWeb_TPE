# Design

## Context

Purchase, Payment Mock, and their databases do not exist yet. Inventory already provides internal idempotent hold, confirm, release, and lookup commands; Catalog provides event detail with prices; Ticket provides idempotent internal QR issuance by order item. The existing services use Java 19, Spring Boot, Maven, PostgreSQL, per-service ownership, and a root Compose network. See the archived architecture blueprint, especially sections 6, 8-12, 20-22.

## Goals / Non-Goals

**Goals:** Implement the E1 purchase flow through Gateway with persisted states, local payment simulation, and ticket integration using existing service contracts.

**Non-Goals:** External payment integrations, broker/framework-based saga, authentication/JWT, refunds initiated by users, event-wide cancellation, and E2 capabilities.

## Decisions

1. **Purchase owns orders and orchestrates synchronously.** A small persisted state machine in Purchase is easier to teach and debug than a saga framework or broker for this scope. Each call uses stable IDs and idempotent service commands; retry resumes the stored order.
2. **Payment Mock is a separate service with PostgreSQL.** This follows the E1 container blueprint and makes payment outcomes durable and inspectable. Use decimal monetary values and explicit currency. A local request field selects `APPROVED` or `REJECTED`; it is a teaching/testing control, not a real payment credential.
3. **Create order and execute purchase as one client operation.** `POST /api/purchases` accepts an `Idempotency-Key`, client identity header, event/items, method, and mock outcome. The response includes the order and its state; `GET /api/purchases/{id}` retrieves progress and issued tickets. This keeps the demo flow short.
4. **Price snapshot comes from Catalog detail.** Purchase copies event, venue, sector, and current price values into its own database before reserving. Inventory remains the only authority for seat/capacity availability; Purchase does not infer availability from Catalog.
   Catalog detail exposes each sector's original source ID, which is the same ID used by Inventory availability and reservation contracts.
5. **Enforce the per-client/event maximum in Purchase's database transaction.** Count `PROCESSING` and `PAID` order items, with a small configurable default maximum of four tickets. Failed, expired, or cancelled orders do not count. Serialize the check-and-create transaction per client/event to avoid concurrent over-limit orders.
6. **Order progression and compensation.** Persist `PENDING`, `RESERVED`, `PAYMENT_APPROVED`, `PAID`, `REJECTED`, or `RECOVERY_REQUIRED`. Sequence: snapshot -> persist idempotent order -> hold inventory -> charge -> confirm hold -> issue tickets -> mark paid. On rejection release the hold. If charge approval is known but confirmation fails because the hold expired, request idempotent refund and leave a recoverable state if any call fails.
7. **Keep internal APIs private.** Gateway routes only `/api/purchases/**`; Purchase calls `catalog-service`, `inventory-service`, `payment-mock`, and `ticket-service` over Compose DNS. No internal command is exposed through Gateway.
8. **Keep runtime local and simple.** Each service gets its own PostgreSQL database, Dockerfile, healthcheck, and configuration. Use existing Spring HTTP and persistence conventions; no broker, Redis, cache, migration framework, or additional observability stack.

## Risks / Trade-offs

- [Synchronous calls can fail between durable steps] -> Persist each state transition and use idempotent downstream calls; expose order status and retry through the same idempotency key.
- [Payment approval followed by lost inventory hold] -> Issue no ticket; request an idempotent refund and preserve `RECOVERY_REQUIRED` for inspection/retry.
- [Per-client limit can serialize hot event purchases] -> This is a deliberate correctness-first database transaction for a didactic local system.
- [Mock outcome in the request could be mistaken for production behavior] -> Document it as a local simulation control and keep Payment Mock internal.

## Migration Plan

Add Purchase and Payment schemas/databases without touching existing service data. Add service containers and internal URLs to root Compose, then route only public Purchase endpoints through Gateway. A rollback removes the new services and routes while leaving Catalog, Inventory, and Ticket data intact.

## Open Questions

None. The mock's request-selected outcome and default four-ticket limit are explicit local defaults and can be changed through configuration.
