package com.ticketya.payment.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RefundResponse(UUID refundId, UUID paymentId, String status,
                             OffsetDateTime createdAt) {
}
