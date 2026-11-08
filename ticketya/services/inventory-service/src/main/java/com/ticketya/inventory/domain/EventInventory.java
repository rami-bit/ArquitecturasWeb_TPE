package com.ticketya.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "event_inventories")
public class EventInventory {
    @Id
    private UUID id;
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;
    @Column(name = "hold_ttl_seconds", nullable = false)
    private int holdTtlSeconds;
    @Column(name = "initialized_at", nullable = false)
    private OffsetDateTime initializedAt;

    protected EventInventory() {
    }

    public EventInventory(UUID id, UUID eventId, UUID snapshotId, int holdTtlSeconds,
                          OffsetDateTime initializedAt) {
        this.id = id;
        this.eventId = eventId;
        this.snapshotId = snapshotId;
        this.holdTtlSeconds = holdTtlSeconds;
        this.initializedAt = initializedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public int getHoldTtlSeconds() {
        return holdTtlSeconds;
    }

    public OffsetDateTime getInitializedAt() {
        return initializedAt;
    }
}
