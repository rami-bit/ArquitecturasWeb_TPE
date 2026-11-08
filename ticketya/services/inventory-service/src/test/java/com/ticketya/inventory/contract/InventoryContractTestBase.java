package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticketya.inventory.InventoryServiceApplication;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base de la suite de contrato de Inventory (caja negra).
 *
 * Sólo referencia la clase de aplicación pública {@code InventoryServiceApplication} y
 * APIs de Spring / JUnit / Testcontainers / Jackson / JdbcTemplate: ningún DTO,
 * controlador, repositorio ni servicio interno del builder.
 *
 * Levanta UNA sola base PostgreSQL por JVM y UN stub de Catalog compartido por todas las
 * clases concretas de la suite. Cada test usa un {@code eventId} aleatorio propio para no
 * depender de limpieza de base.
 */
@SpringBootTest(
        classes = InventoryServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class InventoryContractTestBase {

    protected static final String CORRELATION_HEADER = "X-Correlation-Id";

    /** UUID reservado: el stub de Catalog responde 404 problem+json para este evento. */
    protected static final UUID UNKNOWN_EVENT =
            UUID.fromString("00000000-0000-4000-8000-000000000404");

    /** UUID reservado: el stub de Catalog cierra la conexión sin respuesta (simula caída). */
    protected static final UUID CATALOG_DOWN_EVENT =
            UUID.fromString("00000000-0000-4000-8000-000000000503");

    protected static final ObjectMapper JSON = new ObjectMapper();

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine");

    /** Requests registradas por el stub de Catalog, para assertear propagación. */
    protected static final List<RecordedCatalogRequest> CATALOG_REQUESTS =
            new CopyOnWriteArrayList<>();

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
    static void inventoryContractProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("CATALOG_SERVICE_URL",
                () -> "http://localhost:" + CATALOG_STUB.getAddress().getPort());
        registry.add("INVENTORY_HOLD_TTL_SECONDS", () -> "600");
        // El puerto real lo entrega @LocalServerPort; SERVER_PORT=0 evita fijar 8082.
        registry.add("SERVER_PORT", () -> "0");
        registry.add("server.port", () -> "0");
    }

    @BeforeEach
    void resetStubRecords() {
        CATALOG_REQUESTS.clear();
    }

    // ------------------------------------------------------------------ helpers HTTP

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected ResponseEntity<String> get(String path) {
        return exchange(HttpMethod.GET, path, null, null);
    }

    protected ResponseEntity<String> get(String path, String correlationId) {
        return exchange(HttpMethod.GET, path, null, correlationId);
    }

    protected ResponseEntity<String> post(String path, String body) {
        return exchange(HttpMethod.POST, path, body, null);
    }

    protected ResponseEntity<String> put(String path, String body) {
        return exchange(HttpMethod.PUT, path, body, null);
    }

    protected ResponseEntity<String> exchange(
            HttpMethod method, String path, String body, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.set(CORRELATION_HEADER, correlationId);
        }
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> getAvailability(UUID eventId) {
        return get("/api/inventory/events/" + eventId + "/availability");
    }

    protected ResponseEntity<String> getAvailability(UUID eventId, String correlationId) {
        return get("/api/inventory/events/" + eventId + "/availability", correlationId);
    }

    // ------------------------------------------------------------- helpers de dominio

    /** Layout descubierto desde la disponibilidad pública (sectorId/seatId de Inventory). */
    protected record Layout(UUID numberedSectorId, UUID seatOneId, UUID seatTwoId,
                            UUID generalSectorId) {
    }

    /** Inicializa el inventario del evento y descubre los identificadores de Inventory. */
    protected Layout initializeAndDiscoverLayout(UUID eventId) throws Exception {
        ResponseEntity<String> response = getAvailability(eventId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode sectors = JSON.readTree(response.getBody()).path("sectors");
        JsonNode numbered = findSector(sectors, "NUMBERED");
        JsonNode general = findSector(sectors, "GENERAL_ADMISSION");
        assertThat(numbered).as("sector NUMBERED del snapshot").isNotNull();
        assertThat(general).as("sector GENERAL_ADMISSION del snapshot").isNotNull();
        JsonNode seats = numbered.path("seats");
        assertThat(seats).hasSize(2);
        return new Layout(
                UUID.fromString(numbered.path("sectorId").asText()),
                UUID.fromString(seats.get(0).path("seatId").asText()),
                UUID.fromString(seats.get(1).path("seatId").asText()),
                UUID.fromString(general.path("sectorId").asText()));
    }

    protected static JsonNode findSector(JsonNode sectors, String type) {
        for (JsonNode sector : sectors) {
            if (type.equals(sector.path("type").asText(null))) {
                return sector;
            }
        }
        return null;
    }

    protected static JsonNode sectorNamed(JsonNode sectors, String name) {
        for (JsonNode sector : sectors) {
            if (name.equals(sector.path("name").asText(null))) {
                return sector;
            }
        }
        return null;
    }

    protected String seatItem(UUID sectorId, UUID seatId) {
        return "{\"sectorId\":\"%s\",\"seatId\":\"%s\"}".formatted(sectorId, seatId);
    }

    protected String quantityItem(UUID sectorId, int quantity) {
        return "{\"sectorId\":\"%s\",\"quantity\":%d}".formatted(sectorId, quantity);
    }

    protected String reservationBody(UUID orderId, UUID eventId, String itemsJson) {
        return reservationBody(orderId, eventId, itemsJson, null);
    }

    protected String reservationBody(UUID orderId, UUID eventId, String itemsJson,
                                     Integer ttlSeconds) {
        String ttl = ttlSeconds == null ? "" : "\"ttlSeconds\":%d,".formatted(ttlSeconds);
        return "{\"orderId\":\"%s\",\"eventId\":\"%s\",%s\"items\":[%s]}"
                .formatted(orderId, eventId, ttl, itemsJson);
    }

    /**
     * Simula el vencimiento de una reserva sin esperar el TTL real.
     *
     * Único acoplamiento al esquema permitido en la suite: la tabla `reservations` y la
     * columna `expires_at` existen sólo en Inventory y se tocan exclusivamente para
     * forzar el vencimiento en los escenarios de expiración perezosa.
     */
    protected void forceReservationExpiry(UUID reservationId) {
        jdbcTemplate.update(
                "UPDATE reservations SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' "
                        + "WHERE id = ?",
                reservationId);
    }

    protected JdbcTemplate jdbc() {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate no inyectado para " + getClass());
        }
        return jdbcTemplate;
    }

    // --------------------------------------------------------------- helpers assert

    protected static void assertProblem(ResponseEntity<String> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        MediaType contentType = response.getHeaders().getContentType();
        assertThat(contentType).as("los errores usan application/problem+json").isNotNull();
        assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .as("content-type %s es problem+json", contentType)
                .isTrue();
        JsonNode problem = parseBody(response);
        assertThat(problem.path("status").asInt(-1)).isEqualTo(expected.value());
        assertThat(problem.path("title").asText(null)).isNotBlank();
        assertThat(problem.path("detail").asText(null)).isNotBlank();
        assertThat(problem.path("instance").asText(null)).isNotBlank();
        assertThat(problem.path("correlationId").asText(null)).isNotBlank();
        String header = response.getHeaders().getFirst(CORRELATION_HEADER);
        assertThat(header).as("toda respuesta lleva X-Correlation-Id").isNotBlank();
        assertThat(problem.path("correlationId").asText()).isEqualTo(header);
    }

    protected static JsonNode parseBody(ResponseEntity<String> response) {
        try {
            return JSON.readTree(response.getBody());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Recorre el árbol JSON completo recolectando todos los nombres de propiedad. */
    protected static void collectFieldNames(JsonNode node, List<String> sink) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(sink::add);
            node.forEach(child -> collectFieldNames(child, sink));
        } else if (node.isArray()) {
            node.forEach(child -> collectFieldNames(child, sink));
        }
    }

    protected static long ttlSeconds(JsonNode reservation) {
        OffsetDateTime createdAt = OffsetDateTime.parse(reservation.path("createdAt").asText());
        OffsetDateTime expiresAt = OffsetDateTime.parse(reservation.path("expiresAt").asText());
        return Duration.between(createdAt, expiresAt).getSeconds();
    }

    // ----------------------------------------------------------------- stub de Catalog

    protected record RecordedCatalogRequest(String path, String correlationId) {
    }

    /** URL del stub de Catalog compartido, para arrancar instancias adicionales en tests de concurrencia. */
    protected static String catalogStubUrl() {
        return "http://localhost:" + CATALOG_STUB.getAddress().getPort();
    }

    private static HttpServer startCatalogStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", InventoryContractTestBase::handleCatalogRequest);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "catalog-contract-stub");
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
            String path = exchange.getRequestURI().getRawPath();
            String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
            CATALOG_REQUESTS.add(new RecordedCatalogRequest(path, correlationId));

            if (!path.startsWith("/internal/events/") || !path.endsWith("/snapshot")) {
                respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(404, "Not found", path, correlationId));
                return;
            }
            String rawEventId = path
                    .substring("/internal/events/".length(), path.length() - "/snapshot".length());
            UUID eventId;
            try {
                eventId = UUID.fromString(rawEventId);
            } catch (IllegalArgumentException invalid) {
                respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(404, "Published event not found", path, correlationId));
                return;
            }

            if (UNKNOWN_EVENT.equals(eventId)) {
                respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(404, "Published event not found", path, correlationId));
                return;
            }
            if (CATALOG_DOWN_EVENT.equals(eventId)) {
                // Catálogo caído: se cierra el exchange sin enviar respuesta.
                exchange.close();
                return;
            }
            respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE, snapshotFor(eventId));
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

    private static String problem(int status, String detail, String instance,
                                  String correlationId) {
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

    /**
     * Snapshot determinístico a partir del eventId: sin ninguna propiedad de precio.
     * Sector NUMBERED "Platea" (capacidad 2, asientos A/1 y A/2) y sector
     * GENERAL_ADMISSION "Campo" (capacidad 3, sin asientos).
     */
    private static String snapshotFor(UUID eventId) {
        UUID snapshotId = derive(eventId, "snapshot");
        UUID plateaSector = derive(eventId, "sector:platea");
        UUID campoSector = derive(eventId, "sector:campo");
        UUID seatOne = derive(eventId, "seat:platea:A:1");
        UUID seatTwo = derive(eventId, "seat:platea:A:2");
        return """
                {
                  "eventId": "%s",
                  "snapshotId": "%s",
                  "name": "Evento de contrato",
                  "description": "Snapshot determinístico para la suite de contrato",
                  "eventType": "CONCERT",
                  "startsAt": "2030-06-01T21:00:00Z",
                  "venue": {
                    "name": "Estadio de contrato",
                    "address": "Av. Contrato 100",
                    "city": "Buenos Aires",
                    "latitude": -34.6037,
                    "longitude": -58.3816
                  },
                  "sectors": [
                    {
                      "sourceSectorId": "%s",
                      "name": "Platea",
                      "type": "NUMBERED",
                      "capacity": 2,
                      "seats": [
                        {"sourceSeatId": "%s", "row": "A", "number": 1},
                        {"sourceSeatId": "%s", "row": "A", "number": 2}
                      ]
                    },
                    {
                      "sourceSectorId": "%s",
                      "name": "Campo",
                      "type": "GENERAL_ADMISSION",
                      "capacity": 3,
                      "seats": []
                    }
                  ]
                }
                """.formatted(eventId, snapshotId, plateaSector, seatOne, seatTwo, campoSector);
    }

    private static UUID derive(UUID eventId, String label) {
        return UUID.nameUUIDFromBytes(
                ("%s|%s".formatted(label, eventId)).getBytes(StandardCharsets.UTF_8));
    }
}
