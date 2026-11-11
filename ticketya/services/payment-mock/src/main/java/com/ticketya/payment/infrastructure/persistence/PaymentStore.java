package com.ticketya.payment.infrastructure.persistence;

import com.ticketya.payment.application.Payment;
import com.ticketya.payment.api.RefundResponse;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de pagos sobre PostgreSQL. Sin foreign keys hacia
 * otras bases de datos.
 */
public interface PaymentStore {

    void save(Payment payment);

    Optional<Payment> findById(UUID paymentId);

    Optional<Payment> findByOrderId(UUID orderId);

    int updateStatus(Payment payment);

    RefundResponse createOrGetRefund(UUID paymentId, BigDecimal amount, String key, String fingerprint);

    BigDecimal refundedAmount(UUID paymentId);
}
