package com.ticketya.catalog.infrastructure.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operación crítica a registrar en la bitácora centralizada (E2-02).
 * {@code correlationId} puede ser nulo: el publicador usa entonces el del MDC
 * de la solicitud en curso.
 */
public record AuditEvent(
        String correlationId,
        String operation,
        String outcome,
        String actorId,
        String actorRole,
        String resourceType,
        String resourceId,
        Map<String, Object> details) {

    public AuditEvent {
        details = details == null ? Map.of() : new LinkedHashMap<>(details);
    }

    public static AuditEvent of(String correlationId, String operation, String outcome,
                                String actorId, String actorRole, String resourceType,
                                Object resourceId, Map<String, Object> details) {
        return new AuditEvent(correlationId, operation, outcome, actorId, actorRole, resourceType,
                resourceId == null ? null : resourceId.toString(), details);
    }
}
