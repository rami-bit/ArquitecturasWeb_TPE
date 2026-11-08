package com.ticketya.inventory.application;

import com.ticketya.inventory.config.InventoryProperties;
import com.ticketya.inventory.infrastructure.persistence.InventoryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryInitializationService {
    private final CatalogSnapshotClient catalog;
    private final InventoryStore store;
    private final InventoryProperties properties;

    public InventoryInitializationService(CatalogSnapshotClient catalog, InventoryStore store,
                                          InventoryProperties properties) {
        this.catalog = catalog;
        this.store = store;
        this.properties = properties;
    }

    /**
     * Materializes the inventory of an event exactly once. Two concurrent callers may both
     * fetch the snapshot, but only one wins the unique event_id insert; the other reads the
     * already materialized configuration.
     */
    @Transactional(rollbackFor = Exception.class)
    public InventoryStore.InventoryRow ensure(UUID eventId) {
        Optional<InventoryStore.InventoryRow> existing = store.findInventory(eventId);
        if (existing.isPresent()) {
            return existing.get();
        }

        CatalogSnapshot snapshot = catalog.fetch(eventId);
        UUID inventoryId = UUID.randomUUID();
        OffsetDateTime initializedAt = OffsetDateTime.now(ZoneOffset.UTC);
        boolean inserted = store.insertInventoryIfAbsent(
                inventoryId,
                eventId,
                snapshot.snapshotId(),
                properties.getHoldTtlSeconds(),
                initializedAt);

        InventoryStore.InventoryRow inventory = store.findInventory(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Inventory could not be materialized for event " + eventId));
        if (!inserted) {
            return inventory;
        }

        for (CatalogSnapshot.SnapshotSector sector : safe(snapshot.sectors())) {
            UUID sectorId = UUID.randomUUID();
            store.insertSector(
                    sectorId,
                    inventory.id(),
                    sector.sourceSectorId(),
                    sector.name(),
                    sector.type(),
                    sector.capacity());
            if ("NUMBERED".equals(sector.type())) {
                for (CatalogSnapshot.SnapshotSeat seat : safe(sector.seats())) {
                    store.insertItem(
                            UUID.randomUUID(),
                            inventory.id(),
                            sectorId,
                            seat.sourceSeatId(),
                            seat.row(),
                            seat.number());
                }
            }
        }
        return inventory;
    }

    private static <T> Iterable<T> safe(Iterable<T> values) {
        return values == null ? java.util.List.of() : values;
    }
}
