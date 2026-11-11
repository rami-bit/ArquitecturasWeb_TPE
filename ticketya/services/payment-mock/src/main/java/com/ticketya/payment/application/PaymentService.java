package com.ticketya.payment.application;

import com.ticketya.payment.api.PaymentRequest;
import com.ticketya.payment.api.PaymentResponse;
import com.ticketya.payment.api.RefundRequest;
import com.ticketya.payment.api.RefundResponse;
import com.ticketya.payment.api.error.ApiProblemException;
import com.ticketya.payment.infrastructure.PaymentStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentStore store;

    public PaymentService(PaymentStore store) {
        this.store = store;
    }

    @Transactional
    public PaymentResponse charge(PaymentRequest request) {
        String fingerprint = fingerprint(String.join("|", request.orderId().toString(),
                request.amount().stripTrailingZeros().toPlainString(), request.currency(),
                request.paymentMethod(), request.outcome().name()));
        PaymentResponse payment = store.createOrGetPayment(request, fingerprint);
        if (!payment.orderId().equals(request.orderId())
                || payment.amount().compareTo(request.amount()) != 0
                || !payment.currency().equals(request.currency())
                || !payment.paymentMethod().equals(request.paymentMethod())
                || !payment.status().equals(request.outcome().name())) {
            throw new ApiProblemException(HttpStatus.CONFLICT,
                    "Idempotency conflict", "The idempotency key was already used with another payment");
        }
        return payment;
    }

    @Transactional
    public RefundResponse refund(UUID paymentId, RefundRequest request) {
        PaymentResponse payment = store.payment(paymentId).orElseThrow(() ->
                new ApiProblemException(HttpStatus.NOT_FOUND, "Payment not found",
                        "No payment exists with the supplied identifier"));
        if (!"APPROVED".equals(payment.status())) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "Payment not refundable",
                    "Only an approved payment can be refunded");
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
        return refund;
    }

    private static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
