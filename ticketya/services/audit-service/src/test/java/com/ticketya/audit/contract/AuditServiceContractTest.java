package com.ticketya.audit.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato del Audit Service contra PostgreSQL real: ingesta idempotente,
 * validación, búsqueda con filtros, recorrido por correlation id y
 * autorización exclusiva de ADMIN en las consultas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AuditServiceContractTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM audit_events");
    }

    @Test
    void ingestionIsIdempotentByEventId() throws Exception {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> event = event(eventId, "trace-idem", "purchase-service", "PURCHASE_ORDER",
                "CONFIRMED", OffsetDateTime.now(ZoneOffset.UTC));

        ResponseEntity<String> first = ingest(event);
        ResponseEntity<String> retry = ingest(event);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(json.readTree(first.getBody()).path("duplicate").asBoolean()).isFalse();
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(json.readTree(retry.getBody()).path("duplicate").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE id = ?", Integer.class, eventId))
                .isEqualTo(1);
    }

    @Test
    void invalidEventsAreRejectedWithProblemJson() {
        Map<String, Object> missingCorrelation = event(UUID.randomUUID(), " ", "purchase-service",
                "PURCHASE_ORDER", "CONFIRMED", OffsetDateTime.now(ZoneOffset.UTC));
        Map<String, Object> badOperation = event(UUID.randomUUID(), "trace", "purchase-service",
                "purchase order", "CONFIRMED", OffsetDateTime.now(ZoneOffset.UTC));
        Map<String, Object> arrayDetails = event(UUID.randomUUID(), "trace", "purchase-service",
                "PURCHASE_ORDER", "CONFIRMED", OffsetDateTime.now(ZoneOffset.UTC));
        arrayDetails.put("details", new int[]{1, 2});

        for (Map<String, Object> invalid : new Map[]{missingCorrelation, badOperation, arrayDetails}) {
            ResponseEntity<String> response = ingest(invalid);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class)).isZero();
    }

    @Test
    void traceFollowsOneOperationAcrossServicesInChronologicalOrder() throws Exception {
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        ingest(event(UUID.randomUUID(), "trace-cancel", "purchase-service", "EVENT_CANCELLATION",
                "COMPLETED", base.plusSeconds(3)));
        ingest(event(UUID.randomUUID(), "trace-cancel", "catalog-service", "EVENT_CATALOG_CANCELLATION",
                "CANCELLED", base.plusSeconds(1)));
        ingest(event(UUID.randomUUID(), "trace-cancel", "ticket-service", "TICKET_REVOKED",
                "REVOKED", base.plusSeconds(2)));
        ingest(event(UUID.randomUUID(), "other-trace", "ticket-service", "ACCESS_VALIDATION",
                "ADMITTED", base));

        ResponseEntity<String> response = get("/api/audit/traces/trace-cancel", "ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode trace = json.readTree(response.getBody());
        assertThat(trace.path("eventCount").asInt()).isEqualTo(3);
        assertThat(trace.path("services")).extracting(JsonNode::asText)
                .containsExactly("catalog-service", "ticket-service", "purchase-service");
        assertThat(trace.path("events")).extracting(node -> node.path("operation").asText())
                .containsExactly("EVENT_CATALOG_CANCELLATION", "TICKET_REVOKED", "EVENT_CANCELLATION");
        assertThat(trace.path("events").get(0).path("details").path("source").asText()).isEqualTo("test");
    }

    @Test
    void unknownTraceRespondsNotFound() {
        ResponseEntity<String> response = get("/api/audit/traces/does-not-exist", "ADMIN");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void searchFiltersByOperationAndOutcomeAndPaginates() throws Exception {
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
        for (int i = 0; i < 3; i++) {
            ingest(event(UUID.randomUUID(), "trace-" + i, "ticket-service", "ACCESS_VALIDATION",
                    "ADMITTED", base.plusSeconds(i)));
        }
        ingest(event(UUID.randomUUID(), "trace-x", "ticket-service", "ACCESS_VALIDATION",
                "ALREADY_USED", base.plusSeconds(10)));
        ingest(event(UUID.randomUUID(), "trace-y", "purchase-service", "PURCHASE_ORDER",
                "CONFIRMED", base.plusSeconds(11)));

        JsonNode admitted = json.readTree(get(
                "/api/audit/events?operation=ACCESS_VALIDATION&outcome=ADMITTED&size=2&page=0", "ADMIN").getBody());
        assertThat(admitted.path("totalItems").asLong()).isEqualTo(3);
        assertThat(admitted.path("items")).hasSize(2);
        assertThat(admitted.path("items").get(0).path("correlationId").asText()).isEqualTo("trace-2");

        JsonNode secondPage = json.readTree(get(
                "/api/audit/events?operation=ACCESS_VALIDATION&outcome=ADMITTED&size=2&page=1", "ADMIN").getBody());
        assertThat(secondPage.path("items")).hasSize(1);

        JsonNode byService = json.readTree(get("/api/audit/events?service=purchase-service", "ADMIN").getBody());
        assertThat(byService.path("totalItems").asLong()).isEqualTo(1);

        assertThat(get("/api/audit/events?size=0", "ADMIN").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/audit/events?from=2026-12-01T00:00:00Z&to=2026-01-01T00:00:00Z", "ADMIN")
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onlyAdminCanQueryTheAuditLog() {
        ingest(event(UUID.randomUUID(), "trace-roles", "purchase-service", "PURCHASE_ORDER", "CONFIRMED",
                OffsetDateTime.now(ZoneOffset.UTC)));
        for (String role : new String[]{null, "CLIENT", "ORGANIZER", "ACCESS_STAFF"}) {
            assertThat(get("/api/audit/events", role).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(get("/api/audit/traces/trace-roles", role).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(get("/api/audit/traces/trace-roles", "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void responsesEchoTheCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Role", "ADMIN");
        headers.set("X-Correlation-Id", "audit-echo-1");
        ResponseEntity<String> response = http.exchange("/api/audit/events", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("audit-echo-1");
    }

    private Map<String, Object> event(UUID eventId, String correlationId, String service, String operation,
                                      String outcome, OffsetDateTime occurredAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId.toString());
        event.put("correlationId", correlationId);
        event.put("service", service);
        event.put("operation", operation);
        event.put("outcome", outcome);
        event.put("actorId", UUID.randomUUID().toString());
        event.put("actorRole", "ADMIN");
        event.put("resourceType", "EVENT");
        event.put("resourceId", UUID.randomUUID().toString());
        event.put("occurredAt", occurredAt.toString());
        event.put("details", Map.of("source", "test"));
        return event;
    }

    private ResponseEntity<String> ingest(Map<String, Object> event) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/internal/audit-events", HttpMethod.POST, new HttpEntity<>(event, headers),
                String.class);
    }

    private ResponseEntity<String> get(String path, String role) {
        HttpHeaders headers = new HttpHeaders();
        if (role != null) {
            headers.set("X-User-Role", role);
        }
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
