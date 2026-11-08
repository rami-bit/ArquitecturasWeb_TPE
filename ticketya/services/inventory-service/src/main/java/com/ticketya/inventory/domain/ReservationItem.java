package com.ticketya.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "reservation_items")
public class ReservationItem {
    @Id
    private UUID id;
    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;
    @Column(name = "sector_id", nullable = false)
    private UUID sectorId;
    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;
    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected ReservationItem() {
    }

    public ReservationItem(UUID id, UUID reservationId, UUID sectorId,
                           UUID inventoryItemId, int quantity) {
        this.id = id;
        this.reservationId = reservationId;
        this.sectorId = sectorId;
        this.inventoryItemId = inventoryItemId;
        this.quantity = quantity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getSectorId() {
        return sectorId;
    }

    public UUID getInventoryItemId() {
        return inventoryItemId;
    }

    public int getQuantity() {
        return quantity;
    }
}
