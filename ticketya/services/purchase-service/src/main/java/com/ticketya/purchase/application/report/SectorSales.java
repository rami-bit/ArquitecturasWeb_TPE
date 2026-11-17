package com.ticketya.purchase.application.report;

import java.math.BigDecimal;
import java.util.UUID;

/** Unidades vendidas (órdenes pagadas) y recaudación bruta de un sector. */
public record SectorSales(
        UUID sourceSectorId,
        UUID sectorId,
        String sectorName,
        String sectorType,
        int soldTickets,
        BigDecimal grossRevenue) {
}
