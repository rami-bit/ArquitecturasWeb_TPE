package com.ticketya.payment;

import com.ticketya.payment.api.PaymentRequest;
import com.ticketya.payment.api.PaymentResponse;
import com.ticketya.payment.api.RefundRequest;
import com.ticketya.payment.api.error.ApiProblemException;
import com.ticketya.payment.application.PaymentService;
import com.ticketya.payment.infrastructure.PaymentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceUnitTest {
    @Mock
    private PaymentStore store;

    @InjectMocks
    private PaymentService service;

    @Test
    void chargeStoresAndReturnsConfiguredOutcome() {
        UUID orderId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest(orderId, new BigDecimal("25.00"),
                "ARS", "CARD", "charge-key", PaymentRequest.Outcome.APPROVED);
        PaymentResponse expected = new PaymentResponse(UUID.randomUUID(), orderId,
                new BigDecimal("25.00"), "ARS", "CARD", "APPROVED", OffsetDateTime.now());
        when(store.createOrGetPayment(eq(request), anyString())).thenReturn(expected);

        PaymentResponse actual = service.charge(request);

        assertSame(expected, actual);
        verify(store).createOrGetPayment(eq(request), anyString());
    }

    @Test
    void rejectedPaymentCannotBeRefunded() {
        UUID paymentId = UUID.randomUUID();
        when(store.payment(paymentId)).thenReturn(Optional.of(new PaymentResponse(paymentId,
                UUID.randomUUID(), new BigDecimal("25.00"), "ARS", "CARD", "REJECTED",
                OffsetDateTime.now())));

        ApiProblemException error = assertThrows(ApiProblemException.class,
                () -> service.refund(paymentId, new RefundRequest("refund-key", null)));

        assertEquals(409, error.getStatus().value());
        verify(store, never()).createOrGetRefund(eq(paymentId), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }
}
