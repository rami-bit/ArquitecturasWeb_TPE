package com.ticketya.purchase.infrastructure.audit;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Publicación fire-and-forget con reintentos de la bitácora centralizada (E2-02). */
@ExtendWith(OutputCaptureExtension.class)
class AuditPublisherTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresBeforeSuccess = new AtomicInteger();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int failureStatus = 503;
    private HttpServer server;

    @AfterEach
    void stop() {
        MDC.clear();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsEventWithServiceAndMdcCorrelationIdAndLogsItLocally(CapturedOutput output) throws Exception {
        AuditPublisher publisher = new AuditPublisher(startServer(), "ticket-service", mapper, 3, Duration.ofMillis(10));
        MDC.put("correlationId", "trace-mdc-1");

        publisher.publish(AuditEvent.of(null, "ACCESS_VALIDATION", "ADMITTED", "staff-1", "ACCESS_STAFF",
                "TICKET", "ticket-1", Map.of("gate", "Puerta 1")));

        assertThat(publisher.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(received).singleElement().satisfies(event -> {
            assertThat(event.path("eventId").asText()).isNotBlank();
            assertThat(event.path("correlationId").asText()).isEqualTo("trace-mdc-1");
            assertThat(event.path("service").asText()).isEqualTo("ticket-service");
            assertThat(event.path("operation").asText()).isEqualTo("ACCESS_VALIDATION");
            assertThat(event.path("outcome").asText()).isEqualTo("ADMITTED");
            assertThat(event.path("resourceId").asText()).isEqualTo("ticket-1");
            assertThat(event.path("occurredAt").asText()).isNotBlank();
            assertThat(event.path("details").path("gate").asText()).isEqualTo("Puerta 1");
        });
        assertThat(output.getOut()).contains("audit service=ticket-service operation=ACCESS_VALIDATION outcome=ADMITTED")
                .contains("correlationId=trace-mdc-1");
    }

    @Test
    void retriesTransientFailuresKeepingTheSameEventId() throws Exception {
        failuresBeforeSuccess.set(2);
        AuditPublisher publisher = new AuditPublisher(startServer(), "ticket-service", mapper, 3, Duration.ofMillis(10));

        publisher.publish(AuditEvent.of("trace-retry", "TICKET_REVOCATION", "REVOKED", null, null, "TICKET", "t", null));

        assertThat(publisher.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(calls.get()).isEqualTo(3);
        assertThat(received).hasSize(1);
    }

    @Test
    void doesNotRetryRejectedEvents() throws Exception {
        failuresBeforeSuccess.set(10);
        failureStatus = 400;
        AuditPublisher publisher = new AuditPublisher(startServer(), "ticket-service", mapper, 3, Duration.ofMillis(10));

        publisher.publish(AuditEvent.of("trace-400", "TICKET_REVOCATION", "REVOKED", null, null, "TICKET", "t", null));

        assertThat(publisher.awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void unavailableAuditServiceNeverBreaksTheCaller(CapturedOutput output) {
        AuditPublisher publisher = new AuditPublisher("http://127.0.0.1:1", "ticket-service", mapper, 2,
                Duration.ofMillis(10));

        assertThatCode(() -> publisher.publish(AuditEvent.of("trace-down", "ACCESS_VALIDATION", "ADMITTED",
                null, null, "TICKET", "t", null))).doesNotThrowAnyException();

        assertThat(publisher.awaitIdle(Duration.ofSeconds(10))).isTrue();
        assertThat(output.getOut()).contains("audit service unavailable after 2 attempts");
    }

    @Test
    void localOnlyPublisherOnlyLogs(CapturedOutput output) {
        AuditPublisher publisher = AuditPublisher.localOnly("ticket-service", mapper);
        assertThat(publisher.isRemoteEnabled()).isFalse();
        publisher.publish(AuditEvent.of("trace-local", "TICKETS_ISSUED", "ISSUED", null, null, "ORDER_ITEM", "i", null));
        assertThat(output.getOut()).contains("operation=TICKETS_ISSUED").contains("correlationId=trace-local");
    }

    private String startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/audit-events", exchange -> {
            try (exchange) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                calls.incrementAndGet();
                if (failuresBeforeSuccess.getAndDecrement() > 0) {
                    exchange.sendResponseHeaders(failureStatus, -1);
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
