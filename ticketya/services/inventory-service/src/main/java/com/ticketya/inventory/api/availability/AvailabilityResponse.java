package com.ticketya.inventory.api.availability;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "AvailabilityResponse")
public record AvailabilityResponse(
        UUID eventId,
        OffsetDateTime generatedAt,
        List<SectorAvailability> sectors) {
}
