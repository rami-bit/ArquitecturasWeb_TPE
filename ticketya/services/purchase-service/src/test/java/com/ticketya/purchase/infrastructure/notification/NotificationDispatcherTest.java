package com.ticketya.purchase.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.ticketya.purchase.infrastructure.resilience.CircuitBreaker;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Fallback local del despacho de notificaciones ante fallo o ausencia de adaptador. */
class NotificationDispatcherTest {

    private final AtomicInteger calls = new AtomicInteger();
    private HttpServer server;
    private CircuitBreaker breaker;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() throws IOException {
        calls.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/notifications", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"error\":\"unavailable\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(503, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        breaker = new CircuitBreaker("notifications-test", 2, Duration.ofSeconds(30));
    }

    @AfterEach
    void tearDown() {
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
        server.stop(0);
    }

    @Test
    void sinAdaptadorRemotoNoIntentaEnvioYNuncaLanza() {
        dispatcher = new NotificationDispatcher(null, new ObjectMapper(), breaker);

        assertThat(dispatcher.isRemoteEnabled()).isFalse();
        dispatcher.publish(event());
        dispatcher.publish(null);

        assertThat(calls.get()).isZero();
        assertThat(breaker.failedCalls()).isZero();
    }

    @Test
    void adaptadorCaidoAbreElCircuitoYElFallbackNoAfectaAlLlamador() {
        dispatcher = new NotificationDispatcher(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                new ObjectMapper(), breaker);

        dispatcher.publish(event());
        dispatcher.publish(event());

        Awaitility.await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(breaker.state())
                        .isEqualTo(CircuitBreaker.State.OPEN));
        int callsBefore = calls.get();
        assertThat(callsBefore).isGreaterThanOrEqualTo(2);

        dispatcher.publish(event());
        assertThat(calls.get()).as("con el circuito abierto no se reintenta").isEqualTo(callsBefore);
        assertThat(breaker.rejectedCalls()).isGreaterThanOrEqualTo(1);
    }

    private static NotificationEvent event() {
        return new NotificationEvent("ORDER_CONFIRMED", UUID.randomUUID(),
                UUID.randomUUID(), "trace-notification");
    }
}
