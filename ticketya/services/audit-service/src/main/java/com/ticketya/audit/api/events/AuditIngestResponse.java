package com.ticketya.audit.api.events;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "AuditIngestResponse")
public record AuditIngestResponse(UUID eventId, boolean duplicate) {
}
