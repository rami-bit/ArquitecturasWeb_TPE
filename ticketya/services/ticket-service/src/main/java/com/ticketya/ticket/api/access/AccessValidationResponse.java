package com.ticketya.ticket.api.access;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(name = "AccessValidationResponse")
public record AccessValidationResponse(
        String result,
        UUID ticketId,
        UUID eventId,
        String eventName,
        String sectorName,
        String rowLabel,
        Integer seatNumber,
        String status,
        OffsetDateTime usedAt,
        String gate,
        UUID validatedBy) {
}
