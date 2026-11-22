package com.ticketya.notification.api.inbox;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(name = "CustomerNotification")
public record NotificationView(
        UUID notificationId,
        String type,
        String channel,
        String subject,
        String body,
        String status,
        UUID eventId,
        UUID orderId,
        String correlationId,
        int attempts,
        OffsetDateTime createdAt,
        OffsetDateTime sentAt) {
}
