package com.ticketya.catalog.domain.venue;

import java.util.List;

public record SectorDefinition(String name, SectorType type, Integer capacity, List<SeatPosition> seats) {
    public SectorDefinition {
        if (type == null) throw new IllegalArgumentException("Sector type is required");
        seats = seats == null ? List.of() : List.copyOf(seats);
    }
}
