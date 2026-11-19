package com.ticketya.ticket.api.access;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(name = "AccessScanView")
public record AccessScanView(
        UUID scanId,
        UUID ticketId,
        UUID staffId,
        String gate,
        String result,
        OffsetDateTime scannedAt,
        String correlationId) {
}
