package com.ticketya.catalog.api.snapshot;

import com.ticketya.catalog.domain.venue.SectorType;

import java.util.List;
import java.util.UUID;

public record SnapshotSector(
        UUID sourceSectorId,
        String name,
        SectorType type,
        int capacity,
        List<SnapshotSeat> seats) {
}
