package com.ticketya.ticket.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ticket.api.issuance.TicketIssueRequest;
import com.ticketya.ticket.api.issuance.TicketUnitRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de US-F8 (E2-03) contra PostgreSQL real: el primer escaneo válido
 * marca la entrada como utilizada; los siguientes, las entradas de otro evento,
 * las revocadas y los QR falsificados se rechazan; sólo ACCESS_STAFF escanea;
 * y ante escaneos simultáneos del mismo QR exactamente uno ingresa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AccessControlContractTest {
    private static final String SECRET = "a-local-contract-test-signing-secret-with-more-than-32-bytes";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("ticket.qr.signing-secret", () -> SECRET);
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private final UUID staffId = UUID.randomUUID();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM access_scans");
        jdbc.update("DELETE FROM tickets");
        jdbc.update("DELETE FROM ticket_issuances");
    }

    @Test
    void firstScanAdmitsAndMarksUsedSecondScanIsRejected() throws Exception {
        UUID eventId = UUID.randomUUID();
        Issued ticket = issue(eventId);

        ResponseEntity<String> first = scan(eventId, ticket.qrPayload(), "ACCESS_STAFF", "Puerta 1", "scan-1");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("scan-1");
        JsonNode admitted = json.readTree(first.getBody());
        assertThat(admitted.path("result").asText()).isEqualTo("ADMITTED");
        assertThat(admitted.path("ticketId").asText()).isEqualTo(ticket.ticketId().toString());
        assertThat(admitted.path("status").asText()).isEqualTo("USED");
        assertThat(admitted.path("gate").asText()).isEqualTo("Puerta 1");
        assertThat(admitted.path("validatedBy").asText()).isEqualTo(staffId.toString());
        assertThat(admitted.path("sectorName").asText()).isEqualTo("Platea");
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket.ticketId()))
                .isEqualTo("USED");

        ResponseEntity<String> second = scan(eventId, ticket.qrPayload(), "ACCESS_STAFF", "Puerta 2", "scan-2");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode rejected = json.readTree(second.getBody());
        assertThat(rejected.path("reason").asText()).isEqualTo("ALREADY_USED");
        assertThat(rejected.path("usedGate").asText()).isEqualTo("Puerta 1");
        assertThat(rejected.path("usedAt").asText()).isNotBlank();
        assertThat(rejected.path("correlationId").asText()).isEqualTo("scan-2");

        JsonNode scans = json.readTree(scans(eventId, "ADMIN").getBody());
        assertThat(scans.path("admitted").asLong()).isEqualTo(1);
        assertThat(scans.path("rejected").asLong()).isEqualTo(1);
        assertThat(scans.path("countsByResult").path("ALREADY_USED").asLong()).isEqualTo(1);
        assertThat(scans.path("items")).hasSize(2);
        assertThat(scans.path("items").get(0).path("result").asText()).isEqualTo("ALREADY_USED");
    }

    @Test
    void ticketOfAnotherEventIsRejectedWithoutBeingConsumed() throws Exception {
        UUID eventId = UUID.randomUUID();
        Issued ticket = issue(eventId);

        ResponseEntity<String> wrong = scan(UUID.randomUUID(), ticket.qrPayload(), "ACCESS_STAFF", null, null);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(json.readTree(wrong.getBody()).path("reason").asText()).isEqualTo("WRONG_EVENT");
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket.ticketId()))
                .isEqualTo("ISSUED");

        assertThat(scan(eventId, ticket.qrPayload(), "ACCESS_STAFF", null, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void revokedTicketIsRejectedAndUsedTicketCannotBeRevoked() throws Exception {
        UUID eventId = UUID.randomUUID();
        Issued revoked = issue(eventId);
        assertThat(revoke(revoked.ticketId()).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = scan(eventId, revoked.qrPayload(), "ACCESS_STAFF", null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json.readTree(response.getBody()).path("reason").asText()).isEqualTo("REVOKED");

        Issued used = issue(eventId);
        assertThat(scan(eventId, used.qrPayload(), "ACCESS_STAFF", null, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(revoke(used.ticketId()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void forgedOrMalformedQrIsUnknownTicket() throws Exception {
        UUID eventId = UUID.randomUUID();
        Issued ticket = issue(eventId);
        String tampered = ticket.qrPayload().substring(0, ticket.qrPayload().length() - 2) + "AA";
        String foreignId = "v1." + UUID.randomUUID() + "." + ticket.qrPayload().split("\\.")[2];

        for (String payload : List.of(tampered, foreignId, "not-a-qr", "v2.x.y")) {
            ResponseEntity<String> response = scan(eventId, payload, "ACCESS_STAFF", null, null);
            assertThat(response.getStatusCode()).as(payload).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(json.readTree(response.getBody()).path("reason").asText()).isEqualTo("UNKNOWN_TICKET");
        }
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket.ticketId()))
                .isEqualTo("ISSUED");
        assertThat(scan(eventId, " ", "ACCESS_STAFF", null, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onlyAccessStaffScansAndStaffOrAdminListScans() throws Exception {
        UUID eventId = UUID.randomUUID();
        Issued ticket = issue(eventId);
        for (String role : new String[]{"CLIENT", "ORGANIZER", "ADMIN", null}) {
            assertThat(scan(eventId, ticket.qrPayload(), role, null, null).getStatusCode())
                    .as("rol %s", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket.ticketId()))
                .isEqualTo("ISSUED");
        assertThat(scans(eventId, "CLIENT").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scans(eventId, "ORGANIZER").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scans(eventId, "ACCESS_STAFF").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scans(eventId, "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void concurrentScansOfTheSameQrAdmitExactlyOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        Issued ticket = issue(eventId);
        int attempts = 25;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<HttpStatus>> results = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                String gate = "Molinete " + i;
                Callable<HttpStatus> task = () -> {
                    start.await();
                    return HttpStatus.valueOf(scan(eventId, ticket.qrPayload(), "ACCESS_STAFF", gate, null)
                            .getStatusCode().value());
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            int admitted = 0;
            int conflicts = 0;
            for (Future<HttpStatus> result : results) {
                HttpStatus status = result.get(30, TimeUnit.SECONDS);
                if (status == HttpStatus.OK) {
                    admitted++;
                } else if (status == HttpStatus.CONFLICT) {
                    conflicts++;
                }
            }
            assertThat(admitted).isEqualTo(1);
            assertThat(conflicts).isEqualTo(attempts - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_scans WHERE ticket_id = ? AND result = 'ADMITTED'",
                Integer.class, ticket.ticketId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM access_scans WHERE ticket_id = ? AND result = 'ALREADY_USED'",
                Integer.class, ticket.ticketId())).isEqualTo(attempts - 1);
    }

    // ------------------------------------------------------------------ helpers

    private record Issued(UUID ticketId, String qrPayload) {
    }

    private Issued issue(UUID eventId) throws Exception {
        TicketIssueRequest request = new TicketIssueRequest(UUID.randomUUID(), eventId, "Recital",
                OffsetDateTime.parse("2027-03-10T21:00:00Z"), "Estadio", "Rosario", "Platea",
                List.of(new TicketUnitRequest(1, "F", 7)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = http.exchange(
                "/internal/orders/" + UUID.randomUUID() + "/items/" + UUID.randomUUID() + "/tickets",
                HttpMethod.POST, new HttpEntity<>(request, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode ticket = json.readTree(response.getBody()).path("tickets").get(0);
        return new Issued(UUID.fromString(ticket.path("ticketId").asText()), ticket.path("qrPayload").asText());
    }

    private ResponseEntity<String> scan(UUID eventId, String payload, String role, String gate, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", staffId.toString());
        if (role != null) {
            headers.set("X-User-Role", role);
        }
        if (correlationId != null) {
            headers.set("X-Correlation-Id", correlationId);
        }
        Map<String, Object> body = gate == null ? Map.of("qrPayload", payload)
                : Map.of("qrPayload", payload, "gate", gate);
        return http.exchange("/api/access/events/" + eventId + "/validations", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> scans(UUID eventId, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", UUID.randomUUID().toString());
        headers.set("X-User-Role", role);
        return http.exchange("/api/access/events/" + eventId + "/scans", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> revoke(UUID ticketId) {
        return http.exchange("/internal/tickets/" + ticketId + "/revoke", HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()), String.class);
    }
}
