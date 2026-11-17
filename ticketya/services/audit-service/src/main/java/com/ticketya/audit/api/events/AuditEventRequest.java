package com.ticketya.audit.api.events;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de auditoría enviado por un microservicio productor. {@code eventId}
 * lo genera el productor y vuelve idempotente el reintento del envío.
 */
@Schema(name = "AuditEventRequest")
public record AuditEventRequest(
        @NotNull UUID eventId,
        @NotBlank @Size(max = 120) String correlationId,
        @NotBlank @Size(max = 60) String service,
        @NotBlank @Size(max = 60) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String operation,
        @NotBlank @Size(max = 40) String outcome,
        @Size(max = 64) String actorId,
        @Size(max = 20) String actorRole,
        @Size(max = 40) String resourceType,
        @Size(max = 120) String resourceId,
        @NotNull OffsetDateTime occurredAt,
        JsonNode details) {
}
