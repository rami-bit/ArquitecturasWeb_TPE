package com.ticketya.ticket.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketRevocationTest {
    @Test
    void revocationIsIdempotent() {
        Ticket ticket = new Ticket(UUID.randomUUID(), UUID.randomUUID(), 1,
                UUID.randomUUID(), UUID.randomUUID(), "Event", OffsetDateTime.now().plusDays(5),
                "Venue", "City", "Sector", null, null, "a".repeat(64), OffsetDateTime.now());
        ticket.revoke();
        ticket.revoke();
        assertEquals(TicketStatus.REVOKED, ticket.getStatus());
    }
}
