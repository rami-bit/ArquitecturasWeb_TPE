package com.ticketya.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PaymentMockApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.testcontainers.junit.jupiter.Testcontainers(disabledWithoutDocker = true)
class PaymentContractTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine");
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("server.port", () -> "0");
    }

    @Test
    void chargeIsIdempotentAndDifferentPayloadConflicts() throws Exception {
        UUID orderId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        ResponseEntity<String> first = post("/internal/payments", charge(orderId, key, "APPROVED"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String paymentId = body(first).path("paymentId").asText();

        ResponseEntity<String> replay = post("/internal/payments", charge(orderId, key, "APPROVED"));
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(body(replay).path("paymentId").asText()).isEqualTo(paymentId);

        ResponseEntity<String> conflict = post("/internal/payments", charge(orderId, key, "REJECTED"));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertProblem(conflict);
    }

    @Test
    void onlyApprovedPaymentCanBeRefundedAndRefundIsIdempotent() throws Exception {
        String approvedId = body(post("/internal/payments",
                charge(UUID.randomUUID(), UUID.randomUUID().toString(), "APPROVED")))
                .path("paymentId").asText();
        String refundKey = UUID.randomUUID().toString();
        String refundPath = "/internal/payments/%s/refunds".formatted(approvedId);
        String refundBody = "{\"idempotencyKey\":\"%s\"}".formatted(refundKey);
        ResponseEntity<String> firstRefund = post(refundPath, refundBody);
        ResponseEntity<String> replayRefund = post(refundPath, refundBody);
        assertThat(firstRefund.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(body(firstRefund).path("refundId").asText())
                .isEqualTo(body(replayRefund).path("refundId").asText());

        String rejectedId = body(post("/internal/payments",
                charge(UUID.randomUUID(), UUID.randomUUID().toString(), "REJECTED")))
                .path("paymentId").asText();
        ResponseEntity<String> rejectedRefund = post("/internal/payments/%s/refunds".formatted(rejectedId),
                "{\"idempotencyKey\":\"%s\"}".formatted(UUID.randomUUID()));
        assertThat(rejectedRefund.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertProblem(rejectedRefund);
    }

    private ResponseEntity<String> post(String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-Id", "payment-test-1");
        return rest.exchange("http://localhost:" + port + path, HttpMethod.POST,
                new HttpEntity<>(json, headers), String.class);
    }

    private static String charge(UUID orderId, String key, String outcome) {
        return """
                {"orderId":"%s","amount":1250.00,"currency":"ARS",
                 "paymentMethod":"CARD","idempotencyKey":"%s","outcome":"%s"}
                """.formatted(orderId, key, outcome);
    }

    private static JsonNode body(ResponseEntity<String> response) throws Exception {
        return JSON.readTree(response.getBody());
    }

    private static void assertProblem(ResponseEntity<String> response) throws Exception {
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(body(response).path("correlationId").asText()).isEqualTo("payment-test-1");
    }
}
