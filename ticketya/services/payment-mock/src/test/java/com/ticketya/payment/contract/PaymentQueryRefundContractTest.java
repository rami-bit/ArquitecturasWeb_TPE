package com.ticketya.payment.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de consulta y reintegro: `GET /internal/payments/{paymentId}`,
 * `GET /internal/payments/by-order/{orderId}` y
 * `POST /internal/payments/{paymentId}/refund` idempotente.
 */
@Testcontainers(disabledWithoutDocker = true)
class PaymentQueryRefundContractTest extends PaymentContractTestBase {

    @Test
    void queriesPaymentByIdReturning200Or404() {
        String orderId = newOrderId();
        ResponseEntity<String> created = post("/internal/payments",
                paymentBody(orderId, 40000.00, "ARS", "CARD", "tok_approved"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String paymentId = parseBody(created).path("paymentId").asText();

        ResponseEntity<String> found = get("/internal/payments/" + paymentId, "trace-query-1");
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getHeaders().getFirst(CORRELATION_HEADER)).isEqualTo("trace-query-1");
        JsonNode payment = parseBody(found);
        assertThat(payment.path("paymentId").asText()).isEqualTo(paymentId);
        assertThat(payment.path("orderId").asText()).isEqualTo(orderId);
        assertThat(payment.path("status").asText()).isEqualTo("APPROVED");
        assertThat(payment.path("amount").asDouble())
                .isCloseTo(40000.00, org.assertj.core.data.Offset.offset(0.001));
        assertThat(payment.path("currency").asText()).isEqualTo("ARS");
        assertThat(payment.path("method").asText()).isEqualTo("CARD");
        assertThat(payment.path("createdAt").asText(null)).isNotBlank();
        assertThat(payment.path("updatedAt").asText(null)).isNotBlank();

        ResponseEntity<String> missing = get("/internal/payments/" + UUID.randomUUID());
        assertProblem(missing, HttpStatus.NOT_FOUND);
    }

    @Test
    void queriesPaymentByOrderIdReturning200Or404() {
        String orderId = newOrderId();
        ResponseEntity<String> created = post("/internal/payments",
                paymentBody(orderId, 15000.00, "ARS", "WALLET", "tok_rejected"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String paymentId = parseBody(created).path("paymentId").asText();

        ResponseEntity<String> found = get("/internal/payments/by-order/" + orderId);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode payment = parseBody(found);
        assertThat(payment.path("paymentId").asText()).isEqualTo(paymentId);
        assertThat(payment.path("orderId").asText()).isEqualTo(orderId);
        assertThat(payment.path("status").asText()).isEqualTo("REJECTED");

        ResponseEntity<String> missing = get("/internal/payments/by-order/" + UUID.randomUUID());
        assertProblem(missing, HttpStatus.NOT_FOUND);
    }

    @Test
    void refundingApprovedPaymentIsIdempotent() {
        String orderId = newOrderId();
        ResponseEntity<String> created = post("/internal/payments",
                paymentBody(orderId, 40000.00, "ARS", "CARD", "tok_approved"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String paymentId = parseBody(created).path("paymentId").asText();

        ResponseEntity<String> refunded = postWithoutBody(
                "/internal/payments/" + paymentId + "/refund");
        assertThat(refunded.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode first = parseBody(refunded);
        assertThat(first.path("paymentId").asText()).isEqualTo(paymentId);
        assertThat(first.path("status").asText()).isEqualTo("REFUNDED");

        // Repetir el reintegro devuelve el mismo pago sin duplicar el movimiento.
        ResponseEntity<String> repeated = postWithoutBody(
                "/internal/payments/" + paymentId + "/refund");
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode second = parseBody(repeated);
        assertThat(second.path("paymentId").asText()).isEqualTo(paymentId);
        assertThat(second.path("status").asText()).isEqualTo("REFUNDED");
        assertThat(second.path("updatedAt").asText()).isEqualTo(first.path("updatedAt").asText());

        // Sólo existe un pago para la orden.
        JsonNode byOrder = parseBody(get("/internal/payments/by-order/" + orderId));
        assertThat(byOrder.path("paymentId").asText()).isEqualTo(paymentId);
        assertThat(byOrder.path("status").asText()).isEqualTo("REFUNDED");
    }

    @Test
    void refundingRejectedPaymentReturnsConflictAndKeepsStatus() {
        String orderId = newOrderId();
        ResponseEntity<String> created = post("/internal/payments",
                paymentBody(orderId, 15000.00, "ARS", "CARD", "tok_rejected"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String paymentId = parseBody(created).path("paymentId").asText();

        ResponseEntity<String> conflict = postWithoutBody(
                "/internal/payments/" + paymentId + "/refund");
        assertProblem(conflict, HttpStatus.CONFLICT);

        assertThat(parseBody(get("/internal/payments/" + paymentId))
                .path("status").asText()).isEqualTo("REJECTED");
    }

    @Test
    void refundingPendingPaymentReturnsConflictAndKeepsStatus() {
        String orderId = newOrderId();
        ResponseEntity<String> created = post("/internal/payments",
                paymentBody(orderId, 30000.00, "ARS", "CARD", "tok_pending"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String paymentId = parseBody(created).path("paymentId").asText();

        ResponseEntity<String> conflict = postWithoutBody(
                "/internal/payments/" + paymentId + "/refund");
        assertProblem(conflict, HttpStatus.CONFLICT);

        assertThat(parseBody(get("/internal/payments/" + paymentId))
                .path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void refundingUnknownPaymentReturnsProblemNotFound() {
        ResponseEntity<String> response = postWithoutBody(
                "/internal/payments/" + UUID.randomUUID() + "/refund");

        assertProblem(response, HttpStatus.NOT_FOUND);
    }
}
