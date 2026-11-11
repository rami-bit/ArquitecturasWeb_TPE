package com.ticketya.payment.api;

import com.ticketya.payment.application.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/payments")
@Tag(name = "Payment Mock (internal)")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create or recover a simulated payment")
    public PaymentResponse charge(@Valid @RequestBody PaymentRequest request) {
        return service.charge(request);
    }

    @PostMapping("/{paymentId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create or recover a simulated refund")
    public RefundResponse refund(@PathVariable UUID paymentId,
                                 @Valid @RequestBody RefundRequest request) {
        return service.refund(paymentId, request);
    }
}
