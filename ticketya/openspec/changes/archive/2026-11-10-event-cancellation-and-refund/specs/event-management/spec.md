# Event Management Delta

## Requirement: Event Cancellation

### Scenario: Successful Event Cancellation
- **Given** an event with status `PUBLISHED`.
- **When** the administrator issues a `POST /events/{eventId}/cancel`.
- **Then** the event status changes to `CANCELLED`.
- **And** requests to reserve seats for this event MUST fail with a `400 Bad Request`.
