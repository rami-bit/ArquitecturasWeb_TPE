package com.ticketya.catalog.infrastructure.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** PDF-F11: envío fire-and-forget de cambios de evento con reintentos y log local. */
@ExtendWith(OutputCaptureExtension.class)
class EventNotificationPublisherTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger failures = new AtomicInteger();
    private HttpServer server;

    @AfterEach
    void stop() {
        MDC.clear();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheChangeWithCorrelationIdAndRetriesTransientFailures() throws IOException {
        failures.set(1);
        EventNotificationPublisher publisher = new EventNotificationPublisher(start(), mapper, 3, Duration.ofMillis(10));
        MDC.put("correlationId", "trace-catalog-f11");
        UUID event = UUID.randomUUID();

        publisher.publish(EventChange.of("EVENT_CANCELLED", event, "Festival",
                OffsetDateTime.parse("2030-05-01T20:00:00-03:00"), "Rosario", List.of("Banda Uno")));

        assertThat(publisher.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(received).singleElement().satisfies(body -> {
            assertThat(body.path("type").asText()).isEqualTo("EVENT_CANCELLED");
            assertThat(body.path("eventId").asText()).isEqualTo(event.toString());
            assertThat(body.path("changeId").asText()).isNotBlank();
            assertThat(body.path("city").asText()).isEqualTo("Rosario");
            assertThat(body.path("participants").get(0).asText()).isEqualTo("Banda Uno");
            assertThat(body.path("correlationId").asText()).isEqualTo("trace-catalog-f11");
        });
    }

    @Test
    void unavailableServiceNeverBreaksTheCallerAndKeepsTheLocalLog(CapturedOutput output) {
        EventNotificationPublisher publisher = new EventNotificationPublisher("http://127.0.0.1:1", mapper, 2,
                Duration.ofMillis(10));
        assertThatCode(() -> publisher.publish(EventChange.of("EVENT_SUSPENDED", UUID.randomUUID(), "Festival",
                null, "Rosario", List.of()))).doesNotThrowAnyException();
        assertThat(publisher.awaitIdle(Duration.ofSeconds(10))).isTrue();
        assertThat(output.getOut()).contains("event-change type=EVENT_SUSPENDED")
                .contains("notification service unavailable after 2 attempts");
    }

    @Test
    void withoutUrlOnlyLogsLocally(CapturedOutput output) {
        EventNotificationPublisher publisher = new EventNotificationPublisher("", mapper);
        assertThat(publisher.isRemoteEnabled()).isFalse();
        publisher.publish(EventChange.of("EVENT_PUBLISHED", UUID.randomUUID(), "Festival", null, "Rosario",
                List.of("Banda")));
        assertThat(output.getOut()).contains("event-change type=EVENT_PUBLISHED");
    }

    private String start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/notifications/event-changes", exchange -> {
            try (exchange) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                if (failures.getAndDecrement() > 0) {
                    exchange.sendResponseHeaders(503, -1);
                    return;
                }
                received.add(mapper.readTree(new String(body, StandardCharsets.UTF_8)));
                exchange.sendResponseHeaders(202, -1);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
