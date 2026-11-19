package com.ticketya.audit.api.events;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(name = "AuditEventView")
public record AuditEventView(
        UUID eventId,
        String correlationId,
        String service,
        String operation,
        String outcome,
        String actorId,
        String actorRole,
        String resourceType,
        String resourceId,
        OffsetDateTime occurredAt,
        OffsetDateTime receivedAt,
        JsonNode details) {
}
