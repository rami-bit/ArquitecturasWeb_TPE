package com.ticketya.payment.api.payment;

import com.ticketya.payment.application.PaymentMethod;
import com.ticketya.payment.application.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Respuesta de un pago registrado. Nunca expone el token de simulaci\u00f3n ni
 * datos del comprador.
 */
public record PaymentResponse(

        UUID paymentId,

        UUID orderId,

        PaymentStatus status,

        BigDecimal amount,

        String currency,

        PaymentMethod method,

        Instant createdAt,

        Instant updatedAt

) {
}
