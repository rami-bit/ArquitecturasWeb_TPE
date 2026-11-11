package com.ticketya.payment.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(UUID paymentId, UUID orderId, BigDecimal amount,
                              String currency, String paymentMethod, String status,
                              OffsetDateTime createdAt) {
}
