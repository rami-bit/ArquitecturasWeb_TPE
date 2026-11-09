package com.ticketya.ticket.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.ticketya.ticket.api.issuance.TicketIssueRequest;
import com.ticketya.ticket.api.issuance.TicketUnitRequest;
import com.ticketya.ticket.application.TicketQrSigner;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class TicketIssuanceContractTest {
    private static final String SECRET =
            "a-local-contract-test-signing-secret-with-more-than-32-bytes";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("ticket.qr.signing-secret", () -> SECRET);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TicketQrSigner qrSigner;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM tickets");
        jdbcTemplate.update("DELETE FROM ticket_issuances");
    }

    @Test
    void issuesScannableQrTicketsAndPersistsOnlyThePayloadHash() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        TicketIssueRequest request = request(
                new TicketUnitRequest(1, "A", 12),
                new TicketUnitRequest(2, "A", 13));

        ResponseEntity<String> response = post(orderId, orderItemId, request, "ticket-test-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id"))
                .isEqualTo("ticket-test-1");
        JsonNode body = parse(response);
        assertThat(body.path("tickets")).hasSize(2);
        assertThat(body.path("tickets").get(0).path("seatNumber").asInt()).isEqualTo(12);

        String payload = body.path("tickets").get(0).path("qrPayload").asText();
        byte[] png = Base64.getDecoder()
                .decode(body.path("tickets").get(0).path("qrPngBase64").asText());
        assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        var image = ImageIO.read(new ByteArrayInputStream(png));
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        assertThat(new MultiFormatReader().decode(bitmap).getText()).isEqualTo(payload);

        UUID ticketId = UUID.fromString(
                body.path("tickets").get(0).path("ticketId").asText());
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT qr_hash FROM tickets WHERE id = ?", String.class, ticketId);
        assertThat(storedHash).isEqualTo(qrSigner.hash(payload)).isNotEqualTo(payload);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema='public' AND table_name='tickets' "
                        + "AND column_name='qr_payload'", Integer.class)).isZero();
    }

    @Test
    void identicalRetryReturnsSameTicketsAndQrWithoutDuplicatingRows() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        TicketIssueRequest request = request(new TicketUnitRequest(1, null, null));

        ResponseEntity<String> created = post(orderId, orderItemId, request, null);
        ResponseEntity<String> repeated = post(orderId, orderItemId, request, null);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode firstTicket = parse(created).path("tickets").get(0);
        JsonNode repeatedTicket = parse(repeated).path("tickets").get(0);
        assertThat(repeatedTicket.path("ticketId").asText())
                .isEqualTo(firstTicket.path("ticketId").asText());
        assertThat(repeatedTicket.path("qrPayload").asText())
                .isEqualTo(firstTicket.path("qrPayload").asText());
        assertThat(repeatedTicket.path("qrPngBase64").asText())
                .isEqualTo(firstTicket.path("qrPngBase64").asText());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_issuances WHERE order_id = ? AND order_item_id = ?",
                Integer.class, orderId, orderItemId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tickets", Integer.class)).isEqualTo(1);
    }

    @Test
    void changedRequestWithSameOrderItemReturnsConflictAndPreservesOriginal() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        TicketIssueRequest original = request(new TicketUnitRequest(1, "B", 3));

        assertThat(post(orderId, orderItemId, original, null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<String> conflict = post(orderId, orderItemId,
                request(new TicketUnitRequest(1, "B", 4)), "ticket-conflict");

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode problem = parse(conflict);
        assertThat(problem.path("correlationId").asText()).isEqualTo("ticket-conflict");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tickets", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT seat_number FROM tickets", Integer.class)).isEqualTo(3);
    }

    @Test
    void invalidSeatPairReturnsProblemAndDoesNotPersist() throws Exception {
        ResponseEntity<String> response = post(
                UUID.randomUUID(),
                UUID.randomUUID(),
                request(new TicketUnitRequest(1, "C", null)),
                "ticket-invalid");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(parse(response).path("correlationId").asText()).isEqualTo("ticket-invalid");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_issuances", Integer.class)).isZero();
    }

    @Test
    void duplicateUnitNumbersAreRejectedByRequestValidation() throws Exception {
        ResponseEntity<String> response = post(
                UUID.randomUUID(),
                UUID.randomUUID(),
                request(new TicketUnitRequest(1, null, null),
                        new TicketUnitRequest(1, null, null)),
                null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_issuances", Integer.class)).isZero();
    }

    @Test
    void concurrentIdenticalRequestsCreateOneIssuanceAndOneSetOfTickets() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        TicketIssueRequest request = request(
                new TicketUnitRequest(1, "D", 21),
                new TicketUnitRequest(2, "D", 22));
        String body = objectMapper.writeValueAsString(request);
        int callers = 8;
        var executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);

        try {
            var responses = IntStream.range(0, callers)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Concurrent test did not start");
                        }
                        return postRaw(orderId, orderItemId, body);
                    }))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ResponseEntity<String>> results = responses.stream()
                    .map(future -> {
                        try {
                            return future.get(30, TimeUnit.SECONDS);
                        } catch (Exception exception) {
                            throw new IllegalStateException(
                                    "Concurrent ticket issuance request failed", exception);
                        }
                    })
                    .toList();
            assertThat(results).allSatisfy(response ->
                    assertThat(response.getStatusCode())
                            .isIn(HttpStatus.CREATED, HttpStatus.OK));
            assertThat(results.stream()
                    .filter(response -> response.getStatusCode() == HttpStatus.CREATED))
                    .hasSize(1);
            assertThat(results.stream()
                    .map(response -> parse(response).path("tickets").get(0)
                            .path("ticketId").asText())
                    .distinct()).hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ticket_issuances WHERE order_id = ? AND order_item_id = ?",
                    Integer.class, orderId, orderItemId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tickets", Integer.class)).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void versionedOpenApiContractDescribesOnlyInternalIssuance() throws Exception {
        String contract = Files.readString(
                Path.of(System.getProperty("ticket.openapi.contract")));
        assertThat(contract)
                .contains("/internal/orders/{orderId}/items/{orderItemId}/tickets")
                .contains("qrPayload")
                .contains("qrPngBase64")
                .contains("application/problem+json")
                .doesNotContain("price:");
    }

    private TicketIssueRequest request(TicketUnitRequest... units) {
        return new TicketIssueRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TicketYa Test Event",
                OffsetDateTime.parse("2030-07-14T20:00:00-03:00"),
                "Test Venue",
                "Buenos Aires",
                "Platea",
                List.of(units));
    }

    private ResponseEntity<String> post(
            UUID orderId, UUID orderItemId, TicketIssueRequest request, String correlationId)
            throws Exception {
        return postRaw(orderId, orderItemId, objectMapper.writeValueAsString(request),
                correlationId);
    }

    private ResponseEntity<String> postRaw(UUID orderId, UUID orderItemId, String body) {
        return postRaw(orderId, orderItemId, body, null);
    }

    private ResponseEntity<String> postRaw(
            UUID orderId, UUID orderItemId, String body, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.set("X-Correlation-Id", correlationId);
        }
        return http.exchange(url(orderId, orderItemId), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private String url(UUID orderId, UUID orderItemId) {
        return "http://localhost:" + port + "/internal/orders/" + orderId
                + "/items/" + orderItemId + "/tickets";
    }

    private JsonNode parse(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception exception) {
            throw new IllegalStateException("Ticket service returned invalid JSON", exception);
        }
    }
}
