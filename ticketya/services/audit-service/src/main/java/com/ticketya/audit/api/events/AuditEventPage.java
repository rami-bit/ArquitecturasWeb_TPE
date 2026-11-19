package com.ticketya.audit.api.events;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "AuditEventPage")
public record AuditEventPage(List<AuditEventView> items, int page, int size, long totalItems) {
}
