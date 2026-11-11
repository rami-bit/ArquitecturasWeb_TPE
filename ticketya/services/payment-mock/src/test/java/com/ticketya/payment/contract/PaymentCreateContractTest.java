package com.ticketya.payment.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de `POST /internal/payments`: resultado determinista por token,
 * validación de payload, idempotencia por `orderId` y propagación de
 * `X-Correlation-Id`.
 */
@Testcontainers(disabledWithoutDocker = true)
class PaymentCreateContractTest extends PaymentContractTestBase {

    @Test
    void approvedTokenCreatesApprovedPayment() {
        String orderId = newOrderId();
        String body = paymentBody(orderId, 40000.00, "ARS", "CARD", "tok_approved");

        ResponseEntity<String> response = post("/internal/payments", body, "trace-payment-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst(CORRELATION_HEADER))
                .isEqualTo("trace-payment-1");
        JsonNode payment = parseBody(response);
        assertThat(UUID.fromString(payment.path("paymentId").asText())).isNotNull();
        assertThat(payment.path("orderId").asText()).isEqualTo(orderId);
        assertThat(payment.path("status").asText()).isEqualTo("APPROVED");
        assertThat(payment.path("amount").asDouble()).isCloseTo(40000.00, org.assertj.core.data.Offset.offset(0.001));
        assertThat(payment.path("currency").asText()).isEqualTo("ARS");
        assertThat(payment.path("method").asText()).isEqualTo("CARD");
        assertThat(payment.path("createdAt").asText(null)).isNotBlank();
        assertThat(payment.path("updatedAt").asText(null)).isNotBlank();
        // El token de simulación nunca se expone en la respuesta.
        assertThat(payment.has("token")).as("PaymentResponse no expone token").isFalse();
    }

    @Test
    void rejectedTokenCreatesRejectedPayment() {
        String orderId = newOrderId();
        String body = paymentBody(orderId, 15000.00, "ARS", "WALLET", "tok_rejected");

        ResponseEntity<String> response = post("/internal/payments", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode payment = parseBody(response);
        assertThat(payment.path("status").asText()).isEqualTo("REJECTED");
        assertThat(payment.path("orderId").asText()).isEqualTo(orderId);
        assertThat(payment.path("method").asText()).isEqualTo("WALLET");
    }

    @Test
    void pendingTokenCreatesPendingPayment() {
        String orderId = newOrderId();
        String body = paymentBody(orderId, 30000.00, "ARS", "CASH", "tok_pending");

        ResponseEntity<String> response = post("/internal/payments", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(parseBody(response).path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void unknownSimulationTokenReturnsProblemBadRequest() {
        String orderId = newOrderId();
        String body = paymentBody(orderId, 40000.00, "ARS", "CARD", "tok_magic");

        ResponseEntity<String> response = post("/internal/payments", body, "trace-payment-2");

        assertProblem(response, HttpStatus.BAD_REQUEST);
        assertThat(parseBody(response).path("correlationId").asText())
                .isEqualTo("trace-payment-2");

        // No se registra el pago.
        assertThat(get("/internal/payments/by-order/" + orderId).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidPaymentFieldsReturnProblemBadRequest() {
        String orderId = newOrderId();

        // Monto no positivo.
        assertProblem(post("/internal/payments",
                        paymentBody(orderId, 0.00, "ARS", "CARD", "tok_approved")),
                HttpStatus.BAD_REQUEST);
        assertProblem(post("/internal/payments",
                        paymentBody(orderId, -100.00, "ARS", "CARD", "tok_approved")),
                HttpStatus.BAD_REQUEST);
        // Moneda distinta de ARS.
        assertProblem(post("/internal/payments",
                        paymentBody(orderId, 40000.00, "USD", "CARD", "tok_approved")),
                HttpStatus.BAD_REQUEST);
        // Medio de pago fuera del enum.
        assertProblem(post("/internal/payments",
                        paymentBody(orderId, 40000.00, "ARS", "BITCOIN", "tok_approved")),
                HttpStatus.BAD_REQUEST);
        // orderId ausente.
        assertProblem(post("/internal/payments",
                        "{\"amount\":40000.00,\"currency\":\"ARS\",\"method\":\"CARD\","
                                + "\"token\":\"tok_approved\"}"),
                HttpStatus.BAD_REQUEST);

        // Ninguno de los payloads inválidos registró un pago.
        assertThat(get("/internal/payments/by-order/" + orderId).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void repeatingIdenticalChargeReturnsTheSamePaymentWithoutDuplicates() {
        String orderId = newOrderId();
        String body = paymentBody(orderId, 40000.00, "ARS", "CARD", "tok_approved");

        ResponseEntity<String> created = post("/internal/payments", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String paymentId = parseBody(created).path("paymentId").asText();

        ResponseEntity<String> firstReplay = post("/internal/payments", body);
        assertThat(firstReplay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(firstReplay).path("paymentId").asText()).isEqualTo(paymentId);
        assertThat(parseBody(firstReplay).path("status").asText()).isEqualTo("APPROVED");

        ResponseEntity<String> secondReplay = post("/internal/payments", body);
        assertThat(secondReplay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(secondReplay).path("paymentId").asText()).isEqualTo(paymentId);

        // La consulta única por orden sigue devolviendo el mismo pago.
        JsonNode byOrder = parseBody(get("/internal/payments/by-order/" + orderId));
        assertThat(byOrder.path("paymentId").asText()).isEqualTo(paymentId);
        assertThat(byOrder.path("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void reusingOrderIdWithDifferentContentReturnsConflict() {
        String orderId = newOrderId();
        String original = paymentBody(orderId, 40000.00, "ARS", "CARD", "tok_approved");

        ResponseEntity<String> created = post("/internal/payments", original);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String paymentId = parseBody(created).path("paymentId").asText();

        // Monto distinto.
        assertProblem(post("/internal/payments",
                        paymentBody(orderId, 50000.00, "ARS", "CARD", "tok_approved")),
                HttpStatus.CONFLICT);
        // Medio distinto.
        assertProblem(post("/internal/payments",
                        paymentBody(orderId, 40000.00, "ARS", "WALLET", "tok_approved")),
                HttpStatus.CONFLICT);
        // Token distinto.
        assertProblem(post("/internal/payments",
                        paymentBody(orderId, 40000.00, "ARS", "CARD", "tok_rejected")),
                HttpStatus.CONFLICT);

        // Se conserva el pago original.
        JsonNode byOrder = parseBody(get("/internal/payments/by-order/" + orderId));
        assertThat(byOrder.path("paymentId").asText()).isEqualTo(paymentId);
        assertThat(byOrder.path("status").asText()).isEqualTo("APPROVED");
        assertThat(byOrder.path("amount").asDouble())
                .isCloseTo(40000.00, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void echoesCorrelationIdOnSuccessAndErrorResponses() {
        String orderId = newOrderId();
        String body = paymentBody(orderId, 40000.00, "ARS", "CARD", "tok_approved");

        ResponseEntity<String> created = post("/internal/payments", body, "trace-create-ok");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getFirst(CORRELATION_HEADER))
                .isEqualTo("trace-create-ok");

        // Sin correlation ID el servicio genera uno propio.
        ResponseEntity<String> generated = post("/internal/payments", body);
        assertThat(generated.getHeaders().getFirst(CORRELATION_HEADER)).isNotBlank();

        ResponseEntity<String> badRequest = post("/internal/payments",
                paymentBody(orderId, 10.00, "ARS", "CARD", "tok_unknown"), "trace-create-err");
        assertProblem(badRequest, HttpStatus.BAD_REQUEST);
        assertThat(badRequest.getHeaders().getFirst(CORRELATION_HEADER))
                .isEqualTo("trace-create-err");

        ResponseEntity<String> notFound = get("/internal/payments/" + UUID.randomUUID(),
                "trace-get-err");
        assertProblem(notFound, HttpStatus.NOT_FOUND);
        assertThat(notFound.getHeaders().getFirst(CORRELATION_HEADER))
                .isEqualTo("trace-get-err");
    }
}
