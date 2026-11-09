# Ticket Service verification

## Completed checks

- Maven verification from the aggregator:
  `.\TP3\mvnw.cmd -f ticketya\pom.xml -pl services/ticket-service -am verify`
  completed with exit code 0 and `BUILD SUCCESS` using JDK 19. All 14 tests
  passed: seven unit tests and seven contract tests; none failed or were skipped.
- The seven contract tests used Testcontainers with PostgreSQL 16.4 and verified
  HTTP issuance/error responses, QR decoding and hash-only persistence,
  idempotent retries, conflicting content, request validation, and concurrency.
- `docker compose -f ticketya\docker-compose.yml config --quiet` completed
  successfully with a temporary verification-only value for
  `TICKET_QR_SIGNING_SECRET`. No secret was written to the repository.
- `docker compose -f ticketya\docker-compose.yml up --build -d --wait ticket-service`
  built the Ticket Service image and started `ticket-service` with `ticket-db`;
  both reported healthy. The service returned `{"status":"UP"}` from
  `/actuator/health`, and `/v3/api-docs` exposed the internal ticket issuance
  route.

## Scope still outstanding

Purchase Service does not exist yet, so there is no end-to-end purchase-to-ticket
flow. Purchase must call the internal issuance endpoint only after confirming
payment and inventory. The Compose check intentionally started only Ticket
Service and its database, not the full TicketYa stack. Access validation, QR
revocation, and refunds remain outside this change.
