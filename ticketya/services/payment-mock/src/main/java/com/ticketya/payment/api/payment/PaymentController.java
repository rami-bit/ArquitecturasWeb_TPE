package com.ticketya.payment.api.payment;

import com.ticketya.payment.api.error.ApiProblemException;
import com.ticketya.payment.api.RefundRequest;
import com.ticketya.payment.api.RefundResponse;
import com.ticketya.payment.application.Payment;
import com.ticketya.payment.application.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PaymentResponse> create(@RequestBody @Valid CreatePaymentRequest request) {
        PaymentService.PaymentOutcome outcome = paymentService.createPayment(request);
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(toResponse(outcome.payment()));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getById(@PathVariable("paymentId") UUID paymentId) {
        return paymentService.findById(paymentId)
                .map(payment -> ResponseEntity.ok(toResponse(payment)))
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND, "Not found", "Payment not found"));
    }

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<PaymentResponse> getByOrder(@PathVariable("orderId") UUID orderId) {
        return paymentService.findByOrderId(orderId)
                .map(payment -> ResponseEntity.ok(toResponse(payment)))
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND, "Not found", "Payment not found"));
    }

    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentResponse> refund(@PathVariable("paymentId") UUID paymentId) {
        Payment payment = paymentService.refund(paymentId);
        return ResponseEntity.ok(toResponse(payment));
    }

    @PostMapping(value = "/{paymentId}/refunds", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RefundResponse> refundPartial(@PathVariable("paymentId") UUID paymentId,
            @RequestBody @Valid RefundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.refundPartial(paymentId, request));
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
}
