package com.ticketya.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record RefundRequest(@NotBlank String idempotencyKey,
                            @DecimalMin("0.01") BigDecimal amount) {
}
