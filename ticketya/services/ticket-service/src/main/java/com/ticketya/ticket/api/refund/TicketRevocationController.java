package com.ticketya.ticket.api.refund;

import com.ticketya.ticket.api.error.ApiProblemException;
import com.ticketya.ticket.domain.Ticket;
import com.ticketya.ticket.domain.TicketStatus;
import com.ticketya.ticket.infrastructure.audit.AuditEvent;
import com.ticketya.ticket.infrastructure.audit.AuditPublisher;
import com.ticketya.ticket.infrastructure.persistence.TicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/tickets")
public class TicketRevocationController {
    private final TicketRepository tickets;
    private final AuditPublisher audit;

    public TicketRevocationController(TicketRepository tickets, AuditPublisher audit) {
        this.tickets = tickets;
        this.audit = audit;
    }

    @PostMapping("/{ticketId}/revoke")
    @Transactional
    public RevocationResponse revoke(@PathVariable UUID ticketId) {
        Ticket ticket = tickets.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, "Ticket not found", "Ticket not found"));
        if (ticket.getStatus() == TicketStatus.USED) {
            audit.publish(AuditEvent.of(null, "TICKET_REVOCATION", "REJECTED_USED", null, null, "TICKET",
                    ticketId, Map.of("eventId", ticket.getEventId().toString())));
            throw ApiProblemException.conflict("Used ticket cannot be revoked");
        }
        ticket.revoke();
        audit.publish(AuditEvent.of(null, "TICKET_REVOCATION", "REVOKED", null, null, "TICKET",
                ticketId, Map.of("eventId", ticket.getEventId().toString())));
        return new RevocationResponse(ticket.getId(), ticket.getStatus());
    }

    public record RevocationResponse(UUID ticketId, TicketStatus status) {}
}
