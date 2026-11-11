package com.ticketya.catalog.api.snapshot;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot publicado e inmutable de un evento con el precio unitario de cada
 * sector. Contrato interno de Catalog consumido por Purchase Service para
 * validar los ítems, fijar precios históricos y emitir tickets.
 *
 * Es independiente del snapshot de inventario {@link CatalogEventSnapshot}, que
 * sigue sin precios y lo consume Inventory.
 */
public record CatalogPurchaseSnapshot(
        UUID eventId,
        UUID snapshotId,
        String name,
        String description,
        String eventType,
        OffsetDateTime startsAt,
        SnapshotVenue venue,
        List<PurchaseSnapshotSector> sectors) {
}
