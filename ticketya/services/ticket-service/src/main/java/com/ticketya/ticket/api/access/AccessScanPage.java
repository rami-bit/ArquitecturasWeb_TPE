package com.ticketya.ticket.api.access;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Intentos de ingreso de un evento con el resumen por resultado. */
@Schema(name = "AccessScanPage")
public record AccessScanPage(
        UUID eventId,
        long admitted,
        long rejected,
        Map<String, Long> countsByResult,
        int page,
        int size,
        long totalItems,
        List<AccessScanView> items) {
}
