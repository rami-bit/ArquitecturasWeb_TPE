package com.ticketya.audit.api.events;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/** Recorrido completo de una operación a través de los microservicios. */
@Schema(name = "AuditTrace")
public record AuditTrace(
        String correlationId,
        int eventCount,
        List<String> services,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        List<AuditEventView> events) {
}
