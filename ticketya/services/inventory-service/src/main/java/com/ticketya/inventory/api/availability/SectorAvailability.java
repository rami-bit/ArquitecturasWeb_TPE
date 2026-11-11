package com.ticketya.inventory.api.availability;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "SectorAvailability")
public record SectorAvailability(
        UUID sectorId,
        UUID sourceSectorId,
        String name,
        String type,
        int capacity,
        int sold,
        int reserved,
        int available,
        List<SeatAvailability> seats) {
}
