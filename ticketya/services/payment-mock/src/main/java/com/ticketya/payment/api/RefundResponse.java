package com.ticketya.payment.api;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.UUID;

public record RefundResponse(UUID refundId, UUID paymentId, BigDecimal amount, String status,
                             OffsetDateTime createdAt) {
}
