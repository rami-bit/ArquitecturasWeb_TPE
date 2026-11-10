# Ticket Issuance Delta

## Requirement: Ticket Refund Lifecycle

### Scenario: User Requests Refund Within 72 Hours Deadline
- **Given** a ticket in status `ISSUED`.
- **And** the event start date is more than 72 hours in the future.
- **When** the user sends `POST /tickets/{ticketId}/refund-request`.
- **Then** the ticket status updates to `REFUND_REQUESTED`.

### Scenario: User Requests Refund Less Than 72 Hours Before Event
- **Given** a ticket in status `ISSUED`.
- **And** the event start date is less than 72 hours away.
- **When** the user sends `POST /tickets/{ticketId}/refund-request`.
- **Then** the system returns a `400 Bad Request` with message "Refund deadline expired".
