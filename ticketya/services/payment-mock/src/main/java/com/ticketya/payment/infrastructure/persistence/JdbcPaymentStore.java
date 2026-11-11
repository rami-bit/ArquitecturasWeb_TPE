package com.ticketya.payment.infrastructure.persistence;

import com.ticketya.payment.application.Payment;
import com.ticketya.payment.application.PaymentMethod;
import com.ticketya.payment.application.PaymentStatus;
import com.ticketya.payment.api.RefundResponse;
import com.ticketya.payment.api.error.ApiProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.dao.EmptyResultDataAccessException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPaymentStore implements PaymentStore {

    private final JdbcTemplate jdbc;

    private static final String BASE_COLUMNS =
            "id, order_id, status, amount, currency, method, simulation_token, created_at, updated_at, refunded_at";

    private static final RowMapper<Payment> PAYMENT_MAPPER = (rs, rowNum) -> new Payment(
            (UUID) rs.getObject("id", UUID.class),
            (UUID) rs.getObject("order_id", UUID.class),
            PaymentStatus.valueOf(rs.getString("status")),
            (BigDecimal) rs.getObject("amount", BigDecimal.class),
            rs.getString("currency"),
            PaymentMethod.valueOf(rs.getString("method")),
            rs.getString("simulation_token"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            instant(rs, "refunded_at")
    );

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public JdbcPaymentStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void save(Payment payment) {
        jdbc.update(
                "INSERT INTO payments (" + BASE_COLUMNS + ") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                payment.id(),
                payment.orderId(),
                payment.status().name(),
                payment.amount(),
                payment.currency(),
                payment.method().name(),
                payment.simulationToken(),
                timestamp(payment.createdAt()),
                timestamp(payment.updatedAt()),
                timestamp(payment.refundedAt()));
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @Override
    public Optional<Payment> findById(UUID paymentId) {
        return findOne(
                "SELECT " + BASE_COLUMNS + " FROM payments WHERE id = ?",
                paymentId);
    }

    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        return findOne(
                "SELECT " + BASE_COLUMNS + " FROM payments WHERE order_id = ?",
                orderId);
    }

    @Override
    public int updateStatus(Payment payment) {
        return jdbc.update(
                "UPDATE payments SET status = ?, refunded_at = ?, updated_at = ? WHERE id = ?",
                payment.status().name(),
                timestamp(payment.refundedAt()),
                timestamp(payment.updatedAt()),
                payment.id());
    }

    @Override
    public RefundResponse createOrGetRefund(UUID paymentId, BigDecimal amount, String key, String fingerprint) {
        // The payment row serializes refunds across instances, so the total cannot be exceeded.
        Payment payment;
        try {
            payment = jdbc.queryForObject(
                    "SELECT " + BASE_COLUMNS + " FROM payments WHERE id = ? FOR UPDATE",
                    PAYMENT_MAPPER, paymentId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiProblemException(HttpStatus.NOT_FOUND, "Not found", "Payment not found");
        }
        Optional<RefundResponse> previous = jdbc.query(
                "SELECT * FROM refunds WHERE idempotency_key = ?", this::mapRefund, key)
                .stream().findFirst();
        if (previous.isPresent()) {
            String stored = jdbc.queryForObject(
                    "SELECT request_fingerprint FROM refunds WHERE idempotency_key = ?", String.class, key);
            if (!paymentId.equals(previous.get().paymentId()) || !fingerprint.equals(stored)) {
                throw new ApiProblemException(HttpStatus.CONFLICT, "Idempotency conflict",
                        "The key was already used for another refund");
            }
            return previous.get();
        }
        if (payment.status() != PaymentStatus.APPROVED) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "Payment not refundable",
                    "Only an approved payment can be refunded");
        }
        if (refundedAmount(paymentId).add(amount).compareTo(payment.amount()) > 0) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "Refund exceeds payment",
                    "Total refunds cannot exceed the approved payment");
        }
        UUID refundId = UUID.randomUUID();
        jdbc.update("INSERT INTO refunds (id,payment_id,amount,idempotency_key,request_fingerprint) "
                        + "VALUES (?,?,?,?,?)", refundId, paymentId, amount, key, fingerprint);
        return jdbc.queryForObject("SELECT * FROM refunds WHERE id = ?", this::mapRefund, refundId);
    }

    @Override
    public BigDecimal refundedAmount(UUID paymentId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM refunds WHERE payment_id = ?",
                BigDecimal.class, paymentId);
    }

    private RefundResponse mapRefund(ResultSet rs, int row) throws SQLException {
        return new RefundResponse(rs.getObject("id", UUID.class), rs.getObject("payment_id", UUID.class),
                rs.getBigDecimal("amount"), "REFUNDED", rs.getObject("created_at", OffsetDateTime.class));
    }

    private Optional<Payment> findOne(String sql, Object... params) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, PAYMENT_MAPPER, params));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }
}
