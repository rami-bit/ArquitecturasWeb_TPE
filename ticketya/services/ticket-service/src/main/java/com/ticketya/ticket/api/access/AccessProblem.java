package com.ticketya.ticket.api.access;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

/** problem+json de un ingreso rechazado; {@code reason} indica la causa. */
@Schema(name = "AccessProblem")
public record AccessProblem(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        String correlationId,
        String reason,
        UUID ticketId,
        OffsetDateTime usedAt,
        String usedGate) {
}
