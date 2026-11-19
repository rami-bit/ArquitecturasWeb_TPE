package com.ticketya.purchase.api.report;

import com.ticketya.purchase.api.error.ApiProblemException;
import com.ticketya.purchase.application.report.EventReport;
import com.ticketya.purchase.application.report.OrganizerReportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * US-F12: reporte de resultados de un evento, publicado por el Gateway en
 * {@code GET /api/reports/events/{eventId}}. ORGANIZER sólo consulta sus
 * propios eventos; ADMIN puede consultar cualquiera.
 */
@RestController
@RequestMapping("/api/reports")
public class OrganizerReportController {
    private final OrganizerReportService reports;

    public OrganizerReportController(OrganizerReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/events/{eventId}")
    public EventReport eventReport(
            @PathVariable("eventId") UUID eventId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return reports.eventReport(eventId, requiredUser(userId), role == null ? "" : role.trim(), correlationId);
    }

    private static UUID requiredUser(String header) {
        try {
            if (header == null || header.isBlank()) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException invalid) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "Forbidden", "X-User-Id debe ser un UUID válido");
        }
    }
}
