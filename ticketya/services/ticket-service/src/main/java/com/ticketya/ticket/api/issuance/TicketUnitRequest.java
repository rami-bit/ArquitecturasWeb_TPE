package com.ticketya.ticket.api.issuance;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TicketUnitRequest(
        @Positive int unitNumber,
        @Size(max = 40) String rowLabel,
        @Positive Integer seatNumber) {
}
