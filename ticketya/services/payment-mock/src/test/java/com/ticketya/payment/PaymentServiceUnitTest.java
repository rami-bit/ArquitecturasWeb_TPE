package com.ticketya.payment;

import com.ticketya.payment.api.RefundRequest;
import com.ticketya.payment.api.RefundResponse;
import com.ticketya.payment.api.payment.CreatePaymentRequest;
import com.ticketya.payment.application.Payment;
import com.ticketya.payment.application.PaymentMethod;
import com.ticketya.payment.application.PaymentService;
import com.ticketya.payment.application.PaymentStatus;
import com.ticketya.payment.infrastructure.persistence.PaymentStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceUnitTest {
    private final PaymentStore store = mock(PaymentStore.class);
    private final PaymentService service = new PaymentService(store);

    @Test
    void sameOrderAndPayloadReturnsExistingPayment() {
        UUID orderId = UUID.randomUUID();
        Payment payment = payment(orderId, PaymentStatus.APPROVED);
        when(store.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        var request = new CreatePaymentRequest(orderId.toString(), new BigDecimal("25.00"),
                "ARS", PaymentMethod.CARD, "tok_approved");

        var outcome = service.createPayment(request);

        assertFalse(outcome.created());
        assertEquals(payment.id(), outcome.payment().id());
        assertThrows(RuntimeException.class, () -> service.createPayment(
                new CreatePaymentRequest(orderId.toString(), new BigDecimal("26.00"),
                        "ARS", PaymentMethod.CARD, "tok_approved")));
    }

    @Test
    void partialRefundLeavesPaymentApprovedUntilTheFullAmountIsRefunded() {
        Payment payment = payment(UUID.randomUUID(), PaymentStatus.APPROVED);
        when(store.findById(payment.id())).thenReturn(Optional.of(payment));
        RefundResponse refund = new RefundResponse(UUID.randomUUID(), payment.id(),
                new BigDecimal("10.00"), "REFUNDED", OffsetDateTime.now());
        when(store.createOrGetRefund(eq(payment.id()), eq(new BigDecimal("10.00")),
                eq("ticket-1"), any())).thenReturn(refund);
        when(store.refundedAmount(payment.id())).thenReturn(new BigDecimal("10.00"));

        assertEquals(refund, service.refundPartial(payment.id(),
                new RefundRequest("ticket-1", new BigDecimal("10.00"))));
        verify(store).createOrGetRefund(eq(payment.id()), eq(new BigDecimal("10.00")),
                eq("ticket-1"), any());
    }

    private static Payment payment(UUID orderId, PaymentStatus status) {
        Instant now = Instant.now();
        return new Payment(UUID.randomUUID(), orderId, status, new BigDecimal("25.00"),
                "ARS", PaymentMethod.CARD, "tok_approved", now, now, null);
    }
}
