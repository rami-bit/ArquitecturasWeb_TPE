package com.ticketya.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "inventory_sectors")
public class InventorySector {
    @Id
    private UUID id;
    @Column(name = "event_inventory_id", nullable = false)
    private UUID eventInventoryId;
    @Column(name = "source_sector_id", nullable = false)
    private UUID sourceSectorId;
    @Column(name = "name", nullable = false)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private SectorType type;
    @Column(name = "capacity", nullable = false)
    private int capacity;
    @Column(name = "sold", nullable = false)
    private int sold;
    @Column(name = "reserved", nullable = false)
    private int reserved;

    protected InventorySector() {
    }

    public InventorySector(UUID id, UUID eventInventoryId, UUID sourceSectorId, String name,
                           SectorType type, int capacity, int sold, int reserved) {
        this.id = id;
        this.eventInventoryId = eventInventoryId;
        this.sourceSectorId = sourceSectorId;
        this.name = name;
        this.type = type;
        this.capacity = capacity;
        this.sold = sold;
        this.reserved = reserved;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventInventoryId() {
        return eventInventoryId;
    }

    public UUID getSourceSectorId() {
        return sourceSectorId;
    }

    public String getName() {
        return name;
    }

    public SectorType getType() {
        return type;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getSold() {
        return sold;
    }

    public int getReserved() {
        return reserved;
    }
}
