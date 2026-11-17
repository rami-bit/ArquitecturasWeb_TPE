package com.ticketya.catalog.api.snapshot;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CatalogEventSnapshot(
        UUID eventId,
        UUID snapshotId,
        String name,
        String description,
        String eventType,
        OffsetDateTime startsAt,
        boolean highDemand,
        SnapshotVenue venue,
        List<SnapshotSector> sectors) {
}
