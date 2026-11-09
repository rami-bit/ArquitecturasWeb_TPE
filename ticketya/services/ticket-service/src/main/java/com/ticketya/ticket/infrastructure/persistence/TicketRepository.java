package com.ticketya.ticket.infrastructure.persistence;

import com.ticketya.ticket.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByIssuanceIdOrderByUnitNumberAsc(UUID issuanceId);
}
