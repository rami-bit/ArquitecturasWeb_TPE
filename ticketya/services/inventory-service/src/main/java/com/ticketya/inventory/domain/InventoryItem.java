package com.ticketya.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {
    @Id
    private UUID id;
    @Column(name = "event_inventory_id", nullable = false)
    private UUID eventInventoryId;
    @Column(name = "sector_id", nullable = false)
    private UUID sectorId;
    @Column(name = "source_seat_id", nullable = false)
    private UUID sourceSeatId;
    @Column(name = "row_label", nullable = false)
    private String rowLabel;
    @Column(name = "seat_number", nullable = false)
    private int seatNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SeatStatus status;
    @Column(name = "reservation_id")
    private UUID reservationId;
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
    @Column(name = "sold_at")
    private OffsetDateTime soldAt;

    protected InventoryItem() {
    }

    public InventoryItem(UUID id, UUID eventInventoryId, UUID sectorId, UUID sourceSeatId,
                         String rowLabel, int seatNumber, SeatStatus status) {
        this.id = id;
        this.eventInventoryId = eventInventoryId;
        this.sectorId = sectorId;
        this.sourceSeatId = sourceSeatId;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventInventoryId() {
        return eventInventoryId;
    }

    public UUID getSectorId() {
        return sectorId;
    }

    public UUID getSourceSeatId() {
        return sourceSeatId;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getSoldAt() {
        return soldAt;
    }
}
