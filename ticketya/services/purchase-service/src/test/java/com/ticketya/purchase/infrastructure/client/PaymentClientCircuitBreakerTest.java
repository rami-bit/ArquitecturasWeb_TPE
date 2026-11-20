package com.ticketya.purchase.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.ticketya.purchase.infrastructure.resilience.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El circuito de Payment falla rápido tras una racha de 5xx y se recupera en semiabierto. */
class PaymentClientCircuitBreakerTest {

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicBoolean failing = new AtomicBoolean(true);
    private final AtomicBoolean rejectedPayment = new AtomicBoolean(false);
    private HttpServer server;
    private PaymentClient client;
    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() throws IOException {
        calls.set(0);
        failing.set(true);
        rejectedPayment.set(false);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/payments", exchange -> {
            calls.incrementAndGet();
            if (rejectedPayment.get()) {
                respond(exchange, 402, "{\"status\":\"REJECTED\"}");
            } else if (failing.get()) {
                respond(exchange, 503, "{\"status\":\"UNAVAILABLE\"}");
            } else {
                respond(exchange, 200, "{\"status\":\"APPROVED\",\"paymentId\":\""
                        + UUID.randomUUID() + "\",\"amount\":100,\"currency\":\"ARS\","
                        + "\"createdAt\":\"2026-11-20T12:00:00Z\","
                        + "\"updatedAt\":\"2026-11-20T12:00:00Z\"}");
            }
        });
        server.start();
        breaker = new CircuitBreaker("payment-test", 3, Duration.ofMillis(30));
        client = new PaymentClient("http://127.0.0.1:" + server.getAddress().getPort(),
                new ObjectMapper(), breaker);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void trasTresErroresNoSeVuelveALLamarAPayment() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(this::charge).isInstanceOf(ServiceException.class);
        }
        assertThat(calls.get()).isEqualTo(3);
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(this::charge)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Circuit breaker");
        assertThat(calls.get()).as("no se reintentó contra Payment").isEqualTo(3);
        assertThat(breaker.rejectedCalls()).isEqualTo(1);
    }

    @Test
    void successEnSemiabiertoCierraElCircuito() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(this::charge).isInstanceOf(ServiceException.class);
        }
        failing.set(false);
        Thread.sleep(50);

        client.charge(UUID.randomUUID(), BigDecimal.TEN, "ARS", "CARD",
                "tok_approved", "trace-1");

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test
    void rechazoDePagoNoAbreElCircuito() {
        failing.set(false);
        rejectedPayment.set(true);

        assertThatThrownBy(this::charge).isInstanceOf(ServiceException.class);

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.consecutiveFailures()).isZero();
    }

    private void charge() {
        client.charge(UUID.randomUUID(), BigDecimal.TEN, "ARS", "CARD",
                "tok_approved", "trace-circuit");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
