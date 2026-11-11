package com.ticketya.payment.api;

import jakarta.validation.constraints.NotBlank;

public record RefundRequest(@NotBlank String idempotencyKey) {
}
