package com.ticketya.ticket.api.issuance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TicketIssueRequest(
        @NotNull UUID customerId,
        @NotNull UUID eventId,
        @NotBlank @Size(max = 200) String eventName,
        @NotNull OffsetDateTime eventStartsAt,
        @NotBlank @Size(max = 200) String venueName,
        @NotBlank @Size(max = 120) String venueCity,
        @NotBlank @Size(max = 120) String sectorName,
        @NotEmpty List<@Valid TicketUnitRequest> tickets) {
}
