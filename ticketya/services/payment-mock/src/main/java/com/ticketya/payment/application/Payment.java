package com.ticketya.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad de dominio de un pago. Persistida en {@code payment_db} sin foreign
 * keys hacia otras bases y sin datos del comprador.
 */
public record Payment(

        UUID id,

        UUID orderId,

        PaymentStatus status,

        BigDecimal amount,

        String currency,

        PaymentMethod method,

        String simulationToken,

        Instant createdAt,

        Instant updatedAt,

        Instant refundedAt

) {
}
