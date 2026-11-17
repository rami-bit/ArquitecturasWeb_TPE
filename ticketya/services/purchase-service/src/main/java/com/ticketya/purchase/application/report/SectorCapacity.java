package com.ticketya.purchase.application.report;

import java.math.BigDecimal;
import java.util.UUID;

/** Sector ofertado del evento: capacidad (Inventory) y precio vigente (Catalog). */
public record SectorCapacity(
        UUID sourceSectorId,
        UUID sectorId,
        String sectorName,
        String sectorType,
        Integer capacity,
        BigDecimal unitPrice) {
}
