package com.ticketya.purchase.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Fila de orden de compra recuperada de purchase_db. */
public record OrderRecord(
        UUID id,
        UUID customerId,
        UUID eventId,
        String eventName,
        OffsetDateTime eventStartsAt,
        String venueName,
        String venueCity,
        String currency,
        BigDecimal totalAmount,
        String status,
        UUID paymentId,
        String paymentStatus,
        String paymentMethod,
        OffsetDateTime paymentCreatedAt,
        OffsetDateTime paymentUpdatedAt,
        String idempotencyKey,
        String requestFingerprint,
        UUID reservationId,
        OffsetDateTime reservationExpiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
