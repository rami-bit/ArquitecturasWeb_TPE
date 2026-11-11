package com.ticketya.payment.infrastructure;

import com.ticketya.payment.api.PaymentRequest;
import com.ticketya.payment.api.PaymentResponse;
import com.ticketya.payment.api.RefundResponse;
import com.ticketya.payment.api.error.ApiProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentStore {
    private final JdbcTemplate jdbc;

    public PaymentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PaymentResponse createOrGetPayment(PaymentRequest request, String fingerprint) {
        jdbc.update("""
                INSERT INTO payments (id, order_id, amount, currency, payment_method, status,
                                      idempotency_key, request_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, UUID.randomUUID(), request.orderId(), request.amount(), request.currency(),
                request.paymentMethod(), request.outcome().name(), request.idempotencyKey(), fingerprint);
        PaymentResponse response = paymentByKey(request.idempotencyKey()).orElseThrow();
        String storedFingerprint = jdbc.queryForObject(
                "SELECT request_fingerprint FROM payments WHERE idempotency_key = ?",
                String.class, request.idempotencyKey());
        if (!fingerprint.equals(storedFingerprint)) {
            throw new ApiProblemException(HttpStatus.CONFLICT,
                    "Idempotency conflict", "The idempotency key was already used with another payment");
        }
        return response;
    }

    public Optional<PaymentResponse> payment(UUID id) {
        return jdbc.query("SELECT * FROM payments WHERE id = ?", (RowMapper<PaymentResponse>) this::payment, id).stream().findFirst();
    }

    private Optional<PaymentResponse> paymentByKey(String key) {
        return jdbc.query("SELECT * FROM payments WHERE idempotency_key = ?", (RowMapper<PaymentResponse>) this::payment, key)
                .stream().findFirst();
    }

    public RefundResponse createOrGetRefund(UUID paymentId, BigDecimal amount, String key, String fingerprint) {
        // Serialize refunds for the same charge, including calls from other instances.
        jdbc.queryForObject("SELECT id FROM payments WHERE id = ? FOR UPDATE", UUID.class, paymentId);
        var sameKey = jdbc.query("SELECT * FROM refunds WHERE idempotency_key = ?",
                (rs, row) -> refund(rs, row), key).stream().findFirst();
        if (sameKey.isPresent()) {
            String storedFingerprint = jdbc.queryForObject(
                    "SELECT request_fingerprint FROM refunds WHERE idempotency_key = ?",
                    String.class, key);
            if (!fingerprint.equals(storedFingerprint) || !sameKey.get().paymentId().equals(paymentId)) {
                throw new ApiProblemException(HttpStatus.CONFLICT,
                        "Idempotency conflict", "The idempotency key was already used for another refund");
            }
            return sameKey.get();
        }
        BigDecimal refunded = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM refunds WHERE payment_id = ?", BigDecimal.class, paymentId);
        BigDecimal charged = jdbc.queryForObject("SELECT amount FROM payments WHERE id = ?", BigDecimal.class, paymentId);
        if (refunded.add(amount).compareTo(charged) > 0) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "Refund exceeds payment",
                    "Total refunds cannot exceed the approved payment");
        }
        jdbc.update("""
                INSERT INTO refunds (id, payment_id, amount, idempotency_key, request_fingerprint)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), paymentId, amount, key, fingerprint);
        return jdbc.queryForObject("SELECT * FROM refunds WHERE idempotency_key = ?",
                (rs, row) -> refund(rs, row), key);
    }

    private PaymentResponse payment(ResultSet rs, int row) throws SQLException {
        return new PaymentResponse(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("payment_method"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class));
    }

    private RefundResponse refund(ResultSet rs, int row) throws SQLException {
        return new RefundResponse(rs.getObject("id", UUID.class), rs.getObject("payment_id", UUID.class),
                rs.getBigDecimal("amount"),
                "REFUNDED", rs.getObject("created_at", OffsetDateTime.class));
    }
}
