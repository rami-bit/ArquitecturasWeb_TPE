package com.ticketya.purchase.application.report;

import java.math.BigDecimal;
import java.util.UUID;

public record SectorReport(
        UUID sectorId,
        UUID sourceSectorId,
        String sectorName,
        String sectorType,
        BigDecimal unitPrice,
        Integer capacity,
        int soldTickets,
        int refundedTickets,
        int activeTickets,
        BigDecimal occupancyPercent,
        BigDecimal grossRevenue,
        BigDecimal refundedAmount,
        BigDecimal netRevenue) {
}
