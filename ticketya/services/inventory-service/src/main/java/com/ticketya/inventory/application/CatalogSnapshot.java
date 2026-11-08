package com.ticketya.inventory.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Internal Catalog snapshot contract used only to materialize the inventory layout.
 * It intentionally has no price field.
 */
public record CatalogSnapshot(
        UUID eventId,
        UUID snapshotId,
        String name,
        String description,
        String eventType,
        OffsetDateTime startsAt,
        SnapshotVenue venue,
        List<SnapshotSector> sectors) {

    public record SnapshotVenue(
            String name,
            String address,
            String city,
            BigDecimal latitude,
            BigDecimal longitude) {
    }

    public record SnapshotSector(
            UUID sourceSectorId,
            String name,
            String type,
            int capacity,
            List<SnapshotSeat> seats) {
    }

    public record SnapshotSeat(
            UUID sourceSeatId,
            String row,
            int number) {
    }
}
