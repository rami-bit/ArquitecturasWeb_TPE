package com.ticketya.purchase.application.report;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Reporte de resultados de un evento para su organizador (US-F12). */
public record EventReport(
        UUID eventId,
        String eventName,
        String eventStatus,
        OffsetDateTime eventStartsAt,
        UUID organizerId,
        String currency,
        OffsetDateTime generatedAt,
        boolean capacityAvailable,
        List<SectorReport> sectors,
        ReportTotals totals,
        List<String> warnings) {
}
