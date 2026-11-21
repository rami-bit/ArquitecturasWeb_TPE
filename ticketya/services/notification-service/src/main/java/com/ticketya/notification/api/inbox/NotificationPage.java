package com.ticketya.notification.api.inbox;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "CustomerNotificationPage")
public record NotificationPage(List<NotificationView> items, int page, int size, long totalItems) {
}
