package com.ticketya.catalog.api.snapshot;

import com.ticketya.catalog.domain.venue.SectorType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Sector del snapshot de compra. Agrega {@code price} al sector del snapshot de
 * inventario; conserva {@code sourceSectorId} como identificador de Catalog.
 */
public record PurchaseSnapshotSector(
        UUID sourceSectorId,
        String name,
        SectorType type,
        int capacity,
        BigDecimal price,
        List<SnapshotSeat> seats) {
}
