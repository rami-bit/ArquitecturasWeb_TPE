package com.ticketya.payment.api.payment;

import com.ticketya.payment.application.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Solicitud de cobro simulado. Idempotente por {@code orderId}: el mismo
 * {@code orderId} con el mismo contenido devuelve el pago existente.
 */
public record CreatePaymentRequest(

        @NotNull String orderId,

        @NotNull @Positive BigDecimal amount,

        @NotNull @Pattern(regexp = "ARS") String currency,

        @NotNull PaymentMethod method,

        @NotNull String token

) {
}
