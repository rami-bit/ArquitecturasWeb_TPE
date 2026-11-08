package com.ticketya.inventory.api.reservation;

import com.ticketya.inventory.api.reservation.ReservationItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "ReservationResponse")
public record ReservationResponse(
        UUID reservationId,
        UUID orderId,
        UUID eventId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        List<ReservationItem> items) {
}
