package com.ticketya.queue.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticketya.queue.QueueServiceApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base de la suite de integración de Queue Service.
 *
 * Levanta UNA sola base PostgreSQL real por JVM y UN stub de Catalog embebido
 * que sirve el snapshot interno ({@code /internal/events/{id}/snapshot}) según
 * lo registrado por cada test. Cada test usa eventId/clientId aleatorios
 * propios para no depender de limpieza de base.
 *
 * Único acoplamiento al esquema permitido: los helpers {@code backdateQueueOpening}
 * y {@code forceAdmissionExpiry} tocan {@code queue_counters.opened_at} y
 * {@code queue_entries.admission_expires_at} para simular el paso del tiempo en
 * tandas y vencimientos sin esperas reales (mismo patrón que
 * {@code forceReservationExpiry} en Inventory).
 */
@SpringBootTest(
        classes = QueueServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class QueueIntegrationTestBase {

    protected static final String CORRELATION_HEADER = "X-Correlation-Id";
    protected static final String USER_HEADER = "X-User-Id";
    protected static final String ROLE_HEADER = "X-User-Role";

    protected static final ObjectMapper JSON = new ObjectMapper();

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine");

    /** Cuerpos de snapshot por eventId; ausencia = Catalog responde 404. */
    static final Map<UUID, String> SNAPSHOTS_BY_EVENT = new ConcurrentHashMap<>();
    static volatile boolean catalogDown;

    private static final HttpServer CATALOG_STUB = startCatalogStub();
    private static volatile boolean postgresStarted;

    @LocalServerPort
    protected int port;

    /** Cliente HTTP propio (sin depender del auto-config del contexto). */
    private final TestRestTemplate rest = new TestRestTemplate();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static synchronized void startPostgresOnceForTheWholeJvm() {
        if (!postgresStarted) {
            POSTGRES.start();
            postgresStarted = true;
        }
    }

    @DynamicPropertySource
    static void queueTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("queue.catalog.base-url",
                () -> "http://localhost:" + CATALOG_STUB.getAddress().getPort());
        // El puerto real lo entrega @LocalServerPort; SERVER_PORT=0 evita fijar 8086.
        registry.add("SERVER_PORT", () -> "0");
        registry.add("server.port", () -> "0");
    }

    @BeforeEach
    void resetCatalogStubState() {
        SNAPSHOTS_BY_EVENT.clear();
        catalogDown = false;
    }

    // ------------------------------------------------------- registro de snapshots

    /** Publica el evento con {@code highDemand: true}. */
    protected static void publishEvent(UUID eventId) {
        SNAPSHOTS_BY_EVENT.put(eventId, snapshotBody(eventId, "\"highDemand\":true"));
    }

    /** Publica el evento con {@code highDemand: false}. */
    protected static void publishLowDemandEvent(UUID eventId) {
        SNAPSHOTS_BY_EVENT.put(eventId, snapshotBody(eventId, "\"highDemand\":false"));
    }

    /** Publica el evento sin el campo highDemand: se trata como false. */
    protected static void publishEventWithoutHighDemand(UUID eventId) {
        SNAPSHOTS_BY_EVENT.put(eventId, snapshotBody(eventId, null));
    }

    /** Retira el evento publicado: el stub pasa a responder 404. */
    protected static void unpublishEvent(UUID eventId) {
        SNAPSHOTS_BY_EVENT.remove(eventId);
    }

    /** El stub responde 503 para todo lo que le llegue. */
    protected static void failCatalog() {
        catalogDown = true;
    }

    private static String snapshotBody(UUID eventId, String highDemandField) {
        UUID snapshotId = UUID.nameUUIDFromBytes(
                ("queue-snapshot|" + eventId).getBytes(StandardCharsets.UTF_8));
        String extra = highDemandField == null ? "" : "," + highDemandField;
        return """
                {"eventId":"%s","snapshotId":"%s","name":"Evento de fila virtual"%s,"eventType":"CONCERT"}
                """.formatted(eventId, snapshotId, extra);
    }

    // ------------------------------------------------------------------ helpers HTTP

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected ResponseEntity<String> join(UUID eventId, UUID clientId) {
        return join(eventId, clientId, "CLIENT");
    }

    protected ResponseEntity<String> join(UUID eventId, UUID clientId, String role) {
        return joinRaw(eventId, clientId == null ? null : clientId.toString(), role);
    }

    /** Permite encabezados crudos para probar la identidad no confiable. */
    protected ResponseEntity<String> joinRaw(UUID eventId, String userIdHeader, String roleHeader) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (userIdHeader != null) {
            headers.put(USER_HEADER, userIdHeader);
        }
        if (roleHeader != null) {
            headers.put(ROLE_HEADER, roleHeader);
        }
        return exchange(HttpMethod.POST, "/api/queue/events/" + eventId + "/entries", null, headers);
    }

    protected ResponseEntity<String> poll(UUID eventId, UUID clientId) {
        return exchange(HttpMethod.GET, "/api/queue/events/" + eventId + "/entries/me", null,
                identityHeaders(clientId));
    }

    protected ResponseEntity<String> leave(UUID eventId, UUID clientId) {
        return exchange(HttpMethod.DELETE, "/api/queue/events/" + eventId + "/entries/me", null,
                identityHeaders(clientId));
    }

    protected ResponseEntity<String> validate(UUID eventId, UUID clientId, String token,
                                              String idempotencyKey) {
        StringBuilder body = new StringBuilder();
        body.append("{\"eventId\":\"").append(eventId).append("\"");
        body.append(",\"clientId\":\"").append(clientId).append("\"");
        body.append(",\"token\":\"").append(token == null ? "" : token).append("\"");
        if (idempotencyKey != null) {
            body.append(",\"idempotencyKey\":\"").append(idempotencyKey).append("\"");
        }
        body.append(",\"correlationId\":\"integration-test\"}");
        return exchange(HttpMethod.POST, "/internal/admissions/validate", body.toString(), Map.of());
    }

    private static Map<String, String> identityHeaders(UUID clientId) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(USER_HEADER, clientId.toString());
        headers.put(ROLE_HEADER, "CLIENT");
        return headers;
    }

    protected ResponseEntity<String> exchange(
            HttpMethod method, String path, String body, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(httpHeaders::set);
        return rest.exchange(url(path), method, new HttpEntity<>(body, httpHeaders), String.class);
    }

    // ----------------------------------------------------------- helpers de dominio

    /** Entrada JSON de un join/poll exitoso. */
    protected JsonNode joinedBody(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).as("join/poll debe tener éxito").isIn(
                HttpStatus.CREATED, HttpStatus.OK);
        return parseBody(response);
    }

    protected static JsonNode parseBody(ResponseEntity<String> response) {
        try {
            return JSON.readTree(response.getBody());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    protected static void assertProblem(ResponseEntity<String> response, HttpStatus expected,
                                        String expectedType) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        MediaType contentType = response.getHeaders().getContentType();
        assertThat(contentType).as("los errores usan application/problem+json").isNotNull();
        assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .as("content-type %s es problem+json", contentType).isTrue();
        JsonNode problem = parseBody(response);
        assertThat(problem.path("type").asText(null)).isEqualTo(expectedType);
        assertThat(problem.path("status").asInt(-1)).isEqualTo(expected.value());
        assertThat(problem.path("title").asText(null)).isNotBlank();
        assertThat(problem.path("detail").asText(null)).isNotBlank();
        assertThat(problem.path("instance").asText(null)).isNotBlank();
        String header = response.getHeaders().getFirst(CORRELATION_HEADER);
        assertThat(header).as("toda respuesta lleva X-Correlation-Id").isNotBlank();
        assertThat(problem.path("correlationId").asText(null)).isEqualTo(header);
    }

    // ------------------------------------------------------------ manipulación de tiempo

    /**
     * Simula que la fila abrió hace N segundos sin esperar el reloj real:
     * la próxima promoción perezosa cubrirá exactamente floor(N/intervalo) tandas.
     */
    protected void backdateQueueOpening(UUID eventId, int secondsBack) {
        jdbc().update("""
                UPDATE queue_counters
                SET opened_at = now() - (? * interval '1 second')
                WHERE event_id = ?
                """, secondsBack, eventId);
    }

    /** Simula el vencimiento del token de admisión sin esperar el TTL real. */
    protected void forceAdmissionExpiry(UUID... entryIds) {
        for (UUID entryId : entryIds) {
            jdbc().update("""
                    UPDATE queue_entries
                    SET admission_expires_at = now() - interval '1 second'
                    WHERE id = ?
                    """, entryId);
        }
    }

    protected JdbcTemplate jdbc() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate no inyectado para " + getClass());
        }
        return jdbcTemplate;
    }

    // ------------------------------------------------------------------- stub de Catalog

    private static HttpServer startCatalogStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", QueueIntegrationTestBase::handleCatalogRequest);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "queue-catalog-stub");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void handleCatalogRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
            String prefix = "/internal/events/";
            if (!"GET".equals(method) || !path.startsWith(prefix) || !path.endsWith("/snapshot")) {
                respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(404, "Not found", path, correlationId));
                return;
            }
            UUID eventId;
            try {
                eventId = UUID.fromString(path.substring(prefix.length(),
                        path.length() - "/snapshot".length()));
            } catch (IllegalArgumentException invalid) {
                respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(404, "Published event not found", path, correlationId));
                return;
            }
            if (catalogDown) {
                respond(exchange, 503, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(503, "Catalog is temporarily unavailable", path, correlationId));
                return;
            }
            String body = SNAPSHOTS_BY_EVENT.get(eventId);
            if (body == null) {
                respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(404, "Published event not found", path, correlationId));
                return;
            }
            respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE, body);
        }
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
        if (correlationId != null) {
            exchange.getResponseHeaders().set(CORRELATION_HEADER, correlationId);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String problem(int status, String detail, String instance, String correlationId) {
        String title = switch (status) {
            case 404 -> "Not found";
            case 503 -> "Service unavailable";
            default -> "Error";
        };
        return "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\","
                + "\"instance\":\"%s\",\"correlationId\":\"%s\"}"
                .formatted(title, status, detail, instance,
                        correlationId == null ? "" : correlationId);
    }
}
