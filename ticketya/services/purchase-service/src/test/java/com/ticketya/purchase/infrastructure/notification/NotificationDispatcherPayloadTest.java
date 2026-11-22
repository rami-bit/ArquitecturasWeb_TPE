package com.ticketya.purchase.infrastructure.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.ticketya.purchase.infrastructure.resilience.CircuitBreaker;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** PDF-F11: contenido que Purchase envía a Notification Service y cierre del circuito ante éxito. */
class NotificationDispatcherPayloadTest {
    private final List<JsonNode> received = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private NotificationDispatcher dispatcher;

    @AfterEach
    void tearDown() {
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void confirmedPurchaseCarriesPublicEventDataAndNoPaymentData() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/notifications", exchange -> {
            try (exchange) {
                received.add(mapper.readTree(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8)));
                exchange.sendResponseHeaders(202, -1);
            }
        });
        server.start();
        CircuitBreaker breaker = new CircuitBreaker("notifications-payload", 2, Duration.ofSeconds(30));
        dispatcher = new NotificationDispatcher("http://127.0.0.1:" + server.getAddress().getPort(), mapper, breaker);
        UUID order = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID event = UUID.randomUUID();

        dispatcher.publish(new NotificationEvent("ORDER_CONFIRMED", order, customer, "trace-f11", event,
                "Recital", OffsetDateTime.parse("2027-01-01T20:00:00Z")));

        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> received.size() == 1);
        JsonNode body = received.get(0);
        assertThat(body.path("type").asText()).isEqualTo("ORDER_CONFIRMED");
        assertThat(body.path("orderId").asText()).isEqualTo(order.toString());
        assertThat(body.path("customerId").asText()).isEqualTo(customer.toString());
        assertThat(body.path("eventId").asText()).isEqualTo(event.toString());
        assertThat(body.path("eventName").asText()).isEqualTo("Recital");
        assertThat(body.path("eventStartsAt").asText()).isEqualTo("2027-01-01T20:00Z");
        assertThat(body.path("correlationId").asText()).isEqualTo("trace-f11");
        assertThat(body.toString()).doesNotContain("token").doesNotContain("qr");
        Awaitility.await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED));
    }
}
