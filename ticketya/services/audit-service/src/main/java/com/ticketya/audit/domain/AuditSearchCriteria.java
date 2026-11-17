package com.ticketya.audit.domain;

import java.time.OffsetDateTime;

/** Filtros opcionales de la búsqueda de eventos de auditoría. */
public record AuditSearchCriteria(
        String correlationId,
        String service,
        String operation,
        String outcome,
        String resourceId,
        String actorId,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size) {

    public static final int MAX_PAGE_SIZE = 200;

    public AuditSearchCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
    }
}
