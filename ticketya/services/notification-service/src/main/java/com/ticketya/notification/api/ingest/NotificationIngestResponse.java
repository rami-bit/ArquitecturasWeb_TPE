package com.ticketya.notification.api.ingest;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "NotificationIngestResponse")
public record NotificationIngestResponse(UUID triggerId, String type, boolean duplicate) {
}
