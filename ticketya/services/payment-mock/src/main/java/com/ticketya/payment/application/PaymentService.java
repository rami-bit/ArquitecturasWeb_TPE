package com.ticketya.payment.application;

import com.ticketya.payment.api.error.ApiProblemException;
import com.ticketya.payment.api.payment.CreatePaymentRequest;
import com.ticketya.payment.api.payment.PaymentResponse;
import com.ticketya.payment.infrastructure.persistence.PaymentStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Servicio de pago mock. Decide el resultado del cobro a partir del token de
 * simulaci\u00f3n documentado y garantiza la idempotencia por {@code orderId}
 * y por {@code paymentId} en el reintegro.
 */
@Service
public class PaymentService {

    private final PaymentStore store;

    public PaymentService(PaymentStore store) {
        this.store = store;
    }

    @Transactional
    public PaymentOutcome createPayment(CreatePaymentRequest request) {
        PaymentStatus status = statusForToken(request.token());

        UUID orderId = UUID.fromString(request.orderId());
        Optional<Payment> existing = store.findByOrderId(orderId);
        if (existing.isPresent()) {
            Payment current = existing.get();
            if (sameContent(current, request)) {
                return new PaymentOutcome(current, false);
            }
            throw new ApiProblemException(
                    HttpStatus.CONFLICT, "Conflict",
                    "A payment already exists for this order with different content");
        }

        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Payment payment = new Payment(
                UUID.randomUUID(),
                orderId,
                status,
                request.amount(),
                request.currency(),
                request.method(),
                request.token(),
                now,
                now,
                null);
        store.save(payment);
        return new PaymentOutcome(payment, true);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findById(UUID paymentId) {
        return store.findById(paymentId);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findByOrderId(UUID orderId) {
        return store.findByOrderId(orderId);
    }

    @Transactional
    public Payment refund(UUID paymentId) {
        Payment payment = store.findById(paymentId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND, "Not found", "Payment not found"));

        if (payment.status() == PaymentStatus.REFUNDED) {
            // Reintegro ya efectuado: idempotente, no se modifica el pago.
            return payment;
        }
        BigDecimal amount = request.amount() == null ? payment.amount() : request.amount();
        if (amount.signum() <= 0 || amount.scale() > 2) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "Invalid refund amount",
                    "Refund amount must be positive with at most two decimals");
        }
        String fingerprint = fingerprint(paymentId + "|" + amount.stripTrailingZeros().toPlainString());
        RefundResponse refund = store.createOrGetRefund(paymentId, amount, request.idempotencyKey(), fingerprint);
        if (!refund.paymentId().equals(paymentId)) {
            throw new ApiProblemException(HttpStatus.CONFLICT,
                    "Idempotency conflict", "The idempotency key was already used for another refund");
        }

        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Payment updated = new Payment(
                payment.id(),
                payment.orderId(),
                PaymentStatus.REFUNDED,
                payment.amount(),
                payment.currency(),
                payment.method(),
                payment.simulationToken(),
                payment.createdAt(),
                now,
                now);
        store.updateStatus(updated);
        return updated;
    }

    private static PaymentStatus statusForToken(String token) {
        return switch (token) {
            case "tok_approved" -> PaymentStatus.APPROVED;
            case "tok_rejected" -> PaymentStatus.REJECTED;
            case "tok_pending" -> PaymentStatus.PENDING;
            default -> throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST, "Invalid request", "Unknown simulation token");
        };
    }

    private static boolean sameContent(Payment current, CreatePaymentRequest request) {
        return current.amount().compareTo(request.amount()) == 0
                && current.currency().equals(request.currency())
                && current.method() == request.method()
                && current.simulationToken().equals(request.token());
    }

    private static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.id(),
                payment.orderId(),
                payment.status(),
                payment.amount(),
                payment.currency(),
                payment.method(),
                payment.createdAt(),
                payment.updatedAt());
    }

    /** Resultado de un cobro: el pago y si se creo uno nuevo (201) o ya existia (200). */
    public record PaymentOutcome(Payment payment, boolean created) {
    }
}
