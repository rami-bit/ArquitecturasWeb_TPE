package com.ticketya.purchase.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.ticketya.purchase.PurchaseServiceApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base de la suite de contrato de Purchase Service (caja negra).
 *
 * Sólo referencia la clase de aplicación pública {@code PurchaseServiceApplication} y
 * APIs de Spring / JUnit / Testcontainers / Jackson / JdbcTemplate: ningún DTO,
 * controlador, repositorio ni servicio interno del builder.
 *
 * Levanta UNA sola base PostgreSQL por JVM y CUATRO stubs HTTP locales
 * embebidos (Catalog, Inventory, Payment y Ticket) que registran cada solicitud
 * recibida para poder assertear la secuencia de la saga. Cada test usa un
 * {@code eventId} aleatorio propio para no depender de limpieza de base.
 */
@SpringBootTest(
        classes = PurchaseServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class PurchaseContractTestBase {

    protected static final String CORRELATION_HEADER = "X-Correlation-Id";
    protected static final String USER_HEADER = "X-User-Id";
    protected static final String ROLE_HEADER = "X-User-Role";
    protected static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    /** UUID reservado: el stub de Catalog responde 404 problem+json (evento no comprable). */
    protected static final UUID NOT_PURCHASABLE_EVENT =
            UUID.fromString("00000000-0000-4000-8000-000000000404");

    /** UUID reservado: el stub de Inventory responde 409 al reservar (sin disponibilidad). */
    protected static final UUID NO_AVAILABILITY_EVENT =
            UUID.fromString("00000000-0000-4000-8000-000000000409");

    protected static final ObjectMapper JSON = new ObjectMapper();

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine");

    // --------------------------------------------------------- registro de stubs

    protected record RecordedRequest(String method, String path, String body) {
    }

    protected static final List<RecordedRequest> CATALOG_REQUESTS = new CopyOnWriteArrayList<>();
    protected static final List<RecordedRequest> INVENTORY_REQUESTS = new CopyOnWriteArrayList<>();
    protected static final List<RecordedRequest> PAYMENT_REQUESTS = new CopyOnWriteArrayList<>();
    protected static final List<RecordedRequest> TICKET_REQUESTS = new CopyOnWriteArrayList<>();

    private static final HttpServer CATALOG_STUB =
            startStub("purchase-contract-catalog-stub", PurchaseContractTestBase::handleCatalog);
    private static final HttpServer INVENTORY_STUB =
            startStub("purchase-contract-inventory-stub", PurchaseContractTestBase::handleInventory);
    private static final HttpServer PAYMENT_STUB =
            startStub("purchase-contract-payment-stub", PurchaseContractTestBase::handlePayment);
    private static final HttpServer TICKET_STUB =
            startStub("purchase-contract-ticket-stub", PurchaseContractTestBase::handleTicket);

    private static volatile boolean postgresStarted;

    @LocalServerPort
    protected int port;

    /** Cliente HTTP propio (sin depender del auto-config del contexto). */
    private final TestRestTemplate rest = new TestRestTemplate();

    @BeforeAll
    static synchronized void startPostgresOnceForTheWholeJvm() {
        if (!postgresStarted) {
            POSTGRES.start();
            postgresStarted = true;
        }
    }

    @DynamicPropertySource
    static void purchaseContractProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("CATALOG_SERVICE_URL",
                () -> "http://localhost:" + CATALOG_STUB.getAddress().getPort());
        registry.add("INVENTORY_SERVICE_URL",
                () -> "http://localhost:" + INVENTORY_STUB.getAddress().getPort());
        registry.add("PAYMENT_SERVICE_URL",
                () -> "http://localhost:" + PAYMENT_STUB.getAddress().getPort());
        registry.add("TICKET_SERVICE_URL",
                () -> "http://localhost:" + TICKET_STUB.getAddress().getPort());
        // El puerto real lo entrega @LocalServerPort; SERVER_PORT=0 evita fijar 8084.
        registry.add("SERVER_PORT", () -> "0");
        registry.add("server.port", () -> "0");
    }

    @BeforeEach
    void resetStubRecords() {
        CATALOG_REQUESTS.clear();
        INVENTORY_REQUESTS.clear();
        PAYMENT_REQUESTS.clear();
        TICKET_REQUESTS.clear();
    }

    // ------------------------------------------------------------------ helpers HTTP

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected ResponseEntity<String> get(String path) {
        return exchange(HttpMethod.GET, path, null, Map.of());
    }

    protected ResponseEntity<String> get(String path, Map<String, String> headers) {
        return exchange(HttpMethod.GET, path, null, headers);
    }

    protected ResponseEntity<String> post(String path, String body, Map<String, String> headers) {
        return exchange(HttpMethod.POST, path, body, headers);
    }

    protected ResponseEntity<String> exchange(
            HttpMethod method, String path, String body, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(httpHeaders::set);
        return rest.exchange(url(path), method, new HttpEntity<>(body, httpHeaders), String.class);
    }

    /** Headers estándar de una compra: comprador + clave de idempotencia. */
    protected Map<String, String> buyerHeaders(UUID userId, String idempotencyKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(USER_HEADER, userId.toString());
        headers.put(IDEMPOTENCY_HEADER, idempotencyKey);
        return headers;
    }

    protected ResponseEntity<String> createOrder(String body, Map<String, String> headers) {
        return post("/api/orders", body, headers);
    }

    // ------------------------------------------------------------- helpers de dominio

    /** Layout derivado del eventId: sector Platea (numerado, 2 asientos) y Campo (general). */
    protected record Layout(UUID plateaSectorId, UUID seatOneId, UUID seatTwoId,
                            UUID campoSectorId) {
    }

    protected static Layout layoutFor(UUID eventId) {
        return new Layout(
                derive(eventId, "sector:platea"),
                derive(eventId, "seat:platea:A:1"),
                derive(eventId, "seat:platea:A:2"),
                derive(eventId, "sector:campo"));
    }

    protected static UUID derive(UUID eventId, String label) {
        return UUID.nameUUIDFromBytes(
                ("%s|%s".formatted(label, eventId)).getBytes(StandardCharsets.UTF_8));
    }

    protected static String orderBody(UUID eventId, String itemsJson, String method,
                                      String token) {
        return ("{\"eventId\":\"%s\",\"items\":[%s],\"payment\":{\"method\":\"%s\","
                + "\"token\":\"%s\"}}").formatted(eventId, itemsJson, method, token);
    }

    protected static String seatItem(UUID sectorId, UUID... seatIds) {
        String joined = Arrays.stream(seatIds)
                .map(UUID::toString)
                .map(seatId -> "\"" + seatId + "\"")
                .collect(Collectors.joining(","));
        return "{\"sectorId\":\"%s\",\"seatIds\":[%s]}".formatted(sectorId, joined);
    }

    protected static String quantityItem(UUID sectorId, int quantity) {
        return "{\"sectorId\":\"%s\",\"quantity\":%d}".formatted(sectorId, quantity);
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
        assertThat(problem.path("type").asText(null))
                .as("el problema declara type")
                .isNotBlank();
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

    // ------------------------------------------------------- conteos sobre los stubs

    protected static long countPosts(List<RecordedRequest> requests, String path) {
        return requests.stream()
                .filter(request -> "POST".equals(request.method()) && path.equals(request.path()))
                .count();
    }

    protected static long countPostsWithSuffix(List<RecordedRequest> requests, String suffix) {
        return requests.stream()
                .filter(request -> "POST".equals(request.method())
                        && request.path().endsWith(suffix))
                .count();
    }

    /** Emisiones de tickets: `POST /internal/orders/{orderId}/items/{itemId}/tickets`. */
    protected static long countTicketIssuances() {
        return TICKET_REQUESTS.stream()
                .filter(request -> "POST".equals(request.method())
                        && request.path().startsWith("/internal/orders/")
                        && request.path().endsWith("/tickets"))
                .count();
    }

    // ------------------------------------------------------------------ stub genérico

    private static HttpServer startStub(String name, HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", handler);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
            case 400 -> "Invalid request";
            case 402 -> "Payment required";
            case 403 -> "Forbidden";
            case 404 -> "Not found";
            case 409 -> "Conflict";
            case 503 -> "Service unavailable";
            default -> "Error";
        };
        return "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\","
                + "\"instance\":\"%s\",\"correlationId\":\"%s\"}"
                .formatted(title, status, detail, instance,
                        correlationId == null ? "" : correlationId);
    }

    // ------------------------------------------------------------ stub de Catalog

    private static void handleCatalog(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
            CATALOG_REQUESTS.add(new RecordedRequest(method, path, null));

            String prefix = "/internal/events/";
            String suffix = "/purchase-snapshot";
            if (!"GET".equals(method) || !path.startsWith(prefix) || !path.endsWith(suffix)) {
                respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(404, "Not found", path, correlationId));
                return;
            }
            String rawEventId = path.substring(prefix.length(), path.length() - suffix.length());
            UUID eventId;
            try {
                eventId = UUID.fromString(rawEventId);
            } catch (IllegalArgumentException invalid) {
                respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(404, "Published event not found", path, correlationId));
                return;
            }
            if (NOT_PURCHASABLE_EVENT.equals(eventId)) {
                respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(404, "Published event not found", path, correlationId));
                return;
            }
            respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE,
                    purchaseSnapshotFor(eventId));
        }
    }

    /**
     * Snapshot de compra determinístico: Platea numerada (2 asientos, 15000.00)
     * y Campo de admisión general (500 cupos, 5000.00), con precios.
     */
    private static String purchaseSnapshotFor(UUID eventId) {
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
                  "description": "Snapshot de compra determinístico para la suite de contrato",
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
                      "price": 15000.00,
                      "seats": [
                        {"sourceSeatId": "%s", "row": "A", "number": 1},
                        {"sourceSeatId": "%s", "row": "A", "number": 2}
                      ]
                    },
                    {
                      "sourceSectorId": "%s",
                      "name": "Campo",
                      "type": "GENERAL_ADMISSION",
                      "capacity": 500,
                      "price": 5000.00,
                      "seats": []
                    }
                  ]
                }
                """.formatted(eventId, snapshotId, plateaSector, seatOne, seatTwo, campoSector);
    }

    // ---------------------------------------------------------- stub de Inventory

    private static final Map<String, StubReservation> RESERVATIONS_BY_ID =
            new ConcurrentHashMap<>();
    private static final Map<String, String> RESERVATION_ID_BY_ORDER = new ConcurrentHashMap<>();

    private static final class StubReservation {
        final String reservationId;
        final String orderId;
        final String eventId;
        final JsonNode request;
        final String createdAt;
        volatile String status;

        StubReservation(String reservationId, String orderId, String eventId, JsonNode request,
                        String createdAt) {
            this.reservationId = reservationId;
            this.orderId = orderId;
            this.eventId = eventId;
            this.request = request;
            this.createdAt = createdAt;
            this.status = "ACTIVE";
        }
    }

    private static void handleInventory(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
            String body = readBody(exchange);
            INVENTORY_REQUESTS.add(new RecordedRequest(method, path, body));

            if ("GET".equals(method) && path.startsWith("/api/inventory/events/")
                    && path.endsWith("/availability")) {
                UUID eventId = UUID.fromString(path.substring(
                        "/api/inventory/events/".length(),
                        path.length() - "/availability".length()));
                UUID platea = derive(eventId, "sector:platea");
                UUID campo = derive(eventId, "sector:campo");
                UUID alternateCampo = derive(eventId, "inventory:sector:campo");
                UUID seatOne = derive(eventId, "seat:platea:A:1");
                UUID seatTwo = derive(eventId, "seat:platea:A:2");
                respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE, """
                        {"eventId":"%s","sectors":[
                          {"sectorId":"%s","sourceSectorId":"%s","type":"NUMBERED","seats":[
                            {"seatId":"%s","sourceSeatId":"%s"},
                            {"seatId":"%s","sourceSeatId":"%s"}]},
                          {"sectorId":"%s","sourceSectorId":"%s","type":"GENERAL_ADMISSION","seats":[]},
                          {"sectorId":"%s","sourceSectorId":"%s","type":"GENERAL_ADMISSION","seats":[]}]}
                        """.formatted(eventId, platea, platea, seatOne, seatOne, seatTwo, seatTwo,
                        campo, campo, alternateCampo, campo));
                return;
            }

            if ("POST".equals(method) && path.equals("/internal/reservations")) {
                JsonNode request;
                try {
                    request = JSON.readTree(body);
                } catch (IOException invalid) {
                    respond(exchange, 400, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(400, "Invalid reservation request", path, correlationId));
                    return;
                }
                String orderId = request.path("orderId").asText(null);
                String eventId = request.path("eventId").asText(null);
                if (orderId == null || eventId == null) {
                    respond(exchange, 400, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(400, "Invalid reservation request", path, correlationId));
                    return;
                }
                if (NO_AVAILABILITY_EVENT.toString().equals(eventId)) {
                    respond(exchange, 409, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(409, "One or more requested items are no longer available",
                                    path, correlationId));
                    return;
                }
                String existingId = RESERVATION_ID_BY_ORDER.get(orderId);
                if (existingId != null) {
                    StubReservation existing = RESERVATIONS_BY_ID.get(existingId);
                    respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE,
                            reservationJson(existing));
                    return;
                }
                StubReservation reservation = new StubReservation(
                        UUID.randomUUID().toString(), orderId, eventId, request, now());
                RESERVATIONS_BY_ID.put(reservation.reservationId, reservation);
                RESERVATION_ID_BY_ORDER.put(orderId, reservation.reservationId);
                respond(exchange, 201, MediaType.APPLICATION_JSON_VALUE,
                        reservationJson(reservation));
                return;
            }

            if ("POST".equals(method) && path.startsWith("/internal/reservations/")
                    && path.endsWith("/confirm")) {
                StubReservation reservation = reservationFromPath(path, "/confirm");
                if (reservation == null) {
                    respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(404, "Reservation not found", path, correlationId));
                    return;
                }
                if ("RELEASED".equals(reservation.status)
                        || "EXPIRED".equals(reservation.status)) {
                    respond(exchange, 409, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(409, "Reservation can no longer be confirmed", path,
                                    correlationId));
                    return;
                }
                reservation.status = "CONFIRMED";
                respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE,
                        reservationJson(reservation));
                return;
            }

            if ("POST".equals(method) && path.startsWith("/internal/reservations/")
                    && path.endsWith("/release")) {
                StubReservation reservation = reservationFromPath(path, "/release");
                if (reservation == null) {
                    respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(404, "Reservation not found", path, correlationId));
                    return;
                }
                if ("CONFIRMED".equals(reservation.status)) {
                    respond(exchange, 409, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(409, "Confirmed reservation cannot be released", path,
                                    correlationId));
                    return;
                }
                reservation.status = "RELEASED";
                respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE,
                        reservationJson(reservation));
                return;
            }

            if ("GET".equals(method) && path.startsWith("/internal/reservations/by-order/")) {
                String orderId = path.substring("/internal/reservations/by-order/".length());
                String reservationId = RESERVATION_ID_BY_ORDER.get(orderId);
                StubReservation reservation = reservationId == null
                        ? null : RESERVATIONS_BY_ID.get(reservationId);
                if (reservation == null) {
                    respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(404, "Reservation not found", path, correlationId));
                    return;
                }
                respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE,
                        reservationJson(reservation));
                return;
            }

            if ("GET".equals(method) && path.startsWith("/internal/reservations/")) {
                String reservationId = path.substring("/internal/reservations/".length());
                StubReservation reservation = RESERVATIONS_BY_ID.get(reservationId);
                if (reservation == null) {
                    respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(404, "Reservation not found", path, correlationId));
                    return;
                }
                respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE,
                        reservationJson(reservation));
                return;
            }

            respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    problem(404, "Not found", path, correlationId));
        }
    }

    private static StubReservation reservationFromPath(String path, String suffix) {
        String reservationId = path
                .substring("/internal/reservations/".length(), path.length() - suffix.length());
        return RESERVATIONS_BY_ID.get(reservationId);
    }

    private static String reservationJson(StubReservation reservation) {
        String expiresAt = "ACTIVE".equals(reservation.status)
                ? OffsetDateTime.parse(reservation.createdAt).plusSeconds(600).toString()
                : "null";
        return """
                {
                  "reservationId": "%s",
                  "orderId": "%s",
                  "eventId": "%s",
                  "status": "%s",
                  "expiresAt": "%s",
                  "createdAt": "%s",
                  "items": [%s]
                }
                """.formatted(reservation.reservationId, reservation.orderId, reservation.eventId,
                reservation.status, expiresAt, reservation.createdAt,
                reservationItemsJson(reservation));
    }

    private static String reservationItemsJson(StubReservation reservation) {
        UUID eventId;
        try {
            eventId = UUID.fromString(reservation.eventId);
        } catch (IllegalArgumentException invalid) {
            eventId = null;
        }
        StringBuilder items = new StringBuilder();
        for (JsonNode item : reservation.request.path("items")) {
            if (items.length() > 0) {
                items.append(',');
            }
            String sectorId = item.path("sectorId").asText();
            boolean hasSeat = item.hasNonNull("seatId");
            String seatId = hasSeat ? item.path("seatId").asText() : null;
            int quantity = hasSeat ? 1 : item.path("quantity").asInt(1);

            String sectorName;
            String sectorType;
            if (eventId != null
                    && derive(eventId, "sector:platea").toString().equals(sectorId)) {
                sectorName = "Platea";
                sectorType = "NUMBERED";
            } else if (eventId != null
                    && (derive(eventId, "sector:campo").toString().equals(sectorId)
                    || derive(eventId, "inventory:sector:campo").toString().equals(sectorId))) {
                sectorName = "Campo";
                sectorType = "GENERAL_ADMISSION";
            } else {
                sectorName = hasSeat ? "Sector numerado" : "Sector general";
                sectorType = hasSeat ? "NUMBERED" : "GENERAL_ADMISSION";
            }

            String rowLabel = "null";
            String seatNumber = "null";
            String sourceSeatId = "null";
            if (hasSeat) {
                sourceSeatId = "\"" + seatId + "\"";
                if (eventId != null
                        && derive(eventId, "seat:platea:A:1").toString().equals(seatId)) {
                    rowLabel = "\"A\"";
                    seatNumber = "1";
                } else if (eventId != null
                        && derive(eventId, "seat:platea:A:2").toString().equals(seatId)) {
                    rowLabel = "\"A\"";
                    seatNumber = "2";
                }
            }

            String sourceSectorId = eventId != null
                    && derive(eventId, "inventory:sector:campo").toString().equals(sectorId)
                    ? derive(eventId, "sector:campo").toString() : sectorId;
            items.append("""
                    {"sectorId":"%s","seatId":%s,"quantity":%d,"sourceSectorId":"%s",
                    "sourceSeatId":%s,"sectorName":"%s","sectorType":"%s",
                    "rowLabel":%s,"seatNumber":%s}""".formatted(
                    sectorId,
                    hasSeat ? "\"" + seatId + "\"" : "null",
                    quantity,
                    sourceSectorId,
                    sourceSeatId,
                    sectorName,
                    sectorType,
                    rowLabel,
                    seatNumber));
        }
        return items.toString();
    }

    // ------------------------------------------------------------- stub de Payment

    private static final Map<String, StubPayment> PAYMENTS_BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, String> PAYMENT_ID_BY_ORDER = new ConcurrentHashMap<>();

    private static final class StubPayment {
        final String paymentId;
        final String orderId;
        final double amount;
        final String currency;
        final String method;
        final String token;
        final String createdAt;
        volatile String status;
        volatile String updatedAt;

        StubPayment(String paymentId, String orderId, double amount, String currency,
                    String method, String token, String status, String createdAt) {
            this.paymentId = paymentId;
            this.orderId = orderId;
            this.amount = amount;
            this.currency = currency;
            this.method = method;
            this.token = token;
            this.status = status;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
        }
    }

    private static void handlePayment(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
            String body = readBody(exchange);
            PAYMENT_REQUESTS.add(new RecordedRequest(method, path, body));

            if ("POST".equals(method) && path.equals("/internal/payments")) {
                JsonNode request;
                try {
                    request = JSON.readTree(body);
                } catch (IOException invalid) {
                    request = null;
                }
                if (request == null) {
                    respond(exchange, 400, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(400, "Invalid payment request", path, correlationId));
                    return;
                }
                String orderId = request.path("orderId").asText(null);
                String currency = request.path("currency").asText(null);
                String payMethod = request.path("method").asText(null);
                String token = request.path("token").asText(null);
                double amount = request.path("amount").asDouble(0);
                boolean valid = orderId != null && !orderId.isBlank()
                        && amount > 0
                        && "ARS".equals(currency)
                        && SetOfMethods.contains(payMethod)
                        && token != null;
                if (!valid) {
                    respond(exchange, 400, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(400, "Invalid payment request", path, correlationId));
                    return;
                }
                if (!SetOfTokens.contains(token)) {
                    respond(exchange, 400, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(400, "Unknown simulation token", path, correlationId));
                    return;
                }

                String existingId = PAYMENT_ID_BY_ORDER.get(orderId);
                if (existingId != null) {
                    StubPayment existing = PAYMENTS_BY_ID.get(existingId);
                    boolean sameContent = existing.amount == amount
                            && existing.currency.equals(currency)
                            && existing.method.equals(payMethod)
                            && existing.token.equals(token);
                    if (!sameContent) {
                        respond(exchange, 409, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                problem(409,
                                        "A payment already exists for this order with different content",
                                        path, correlationId));
                        return;
                    }
                    respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE,
                            paymentJson(existing));
                    return;
                }

                String status = switch (token) {
                    case "tok_approved" -> "APPROVED";
                    case "tok_rejected" -> "REJECTED";
                    default -> "PENDING";
                };
                StubPayment payment = new StubPayment(UUID.randomUUID().toString(), orderId,
                        amount, currency, payMethod, token, status, now());
                PAYMENTS_BY_ID.put(payment.paymentId, payment);
                PAYMENT_ID_BY_ORDER.put(orderId, payment.paymentId);
                respond(exchange, 201, MediaType.APPLICATION_JSON_VALUE, paymentJson(payment));
                return;
            }

            if ("GET".equals(method) && path.startsWith("/internal/payments/by-order/")) {
                String orderId = path.substring("/internal/payments/by-order/".length());
                String paymentId = PAYMENT_ID_BY_ORDER.get(orderId);
                StubPayment payment = paymentId == null ? null : PAYMENTS_BY_ID.get(paymentId);
                if (payment == null) {
                    respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(404, "Payment not found", path, correlationId));
                    return;
                }
                respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE, paymentJson(payment));
                return;
            }

            if ("GET".equals(method) && path.startsWith("/internal/payments/")) {
                String paymentId = path.substring("/internal/payments/".length());
                StubPayment payment = PAYMENTS_BY_ID.get(paymentId);
                if (payment == null) {
                    respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(404, "Payment not found", path, correlationId));
                    return;
                }
                respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE, paymentJson(payment));
                return;
            }

            if ("POST".equals(method) && path.startsWith("/internal/payments/")
                    && path.endsWith("/refund")) {
                String paymentId = path
                        .substring("/internal/payments/".length(), path.length() - "/refund".length());
                StubPayment payment = PAYMENTS_BY_ID.get(paymentId);
                if (payment == null) {
                    respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(404, "Payment not found", path, correlationId));
                    return;
                }
                if ("REJECTED".equals(payment.status) || "PENDING".equals(payment.status)) {
                    respond(exchange, 409, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            problem(409, "Only an approved payment can be refunded", path,
                                    correlationId));
                    return;
                }
                payment.status = "REFUNDED";
                payment.updatedAt = now();
                respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE, paymentJson(payment));
                return;
            }

            respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    problem(404, "Not found", path, correlationId));
        }
    }

    private static final class SetOfMethods {
        static final List<String> VALUES = List.of("CARD", "WALLET", "CASH");

        static boolean contains(String value) {
            return value != null && VALUES.contains(value);
        }
    }

    private static final class SetOfTokens {
        static final List<String> VALUES =
                List.of("tok_approved", "tok_rejected", "tok_pending");

        static boolean contains(String value) {
            return value != null && VALUES.contains(value);
        }
    }

    private static String paymentJson(StubPayment payment) {
        return """
                {
                  "paymentId": "%s",
                  "orderId": "%s",
                  "status": "%s",
                  "amount": %s,
                  "currency": "%s",
                  "method": "%s",
                  "createdAt": "%s",
                  "updatedAt": "%s"
                }
                """.formatted(payment.paymentId, payment.orderId, payment.status,
                String.format(Locale.ROOT, "%.2f", payment.amount), payment.currency,
                payment.method, payment.createdAt, payment.updatedAt);
    }

    // -------------------------------------------------------------- stub de Ticket

    private static final Map<String, String> TICKET_RESPONSES = new ConcurrentHashMap<>();

    private static void handleTicket(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
            String body = readBody(exchange);
            TICKET_REQUESTS.add(new RecordedRequest(method, path, body));

            boolean matches = "POST".equals(method)
                    && path.startsWith("/internal/orders/")
                    && path.contains("/items/")
                    && path.endsWith("/tickets");
            if (!matches) {
                respond(exchange, 404, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(404, "Not found", path, correlationId));
                return;
            }

            String existing = TICKET_RESPONSES.get(path);
            if (existing != null) {
                respond(exchange, 200, MediaType.APPLICATION_JSON_VALUE, existing);
                return;
            }

            JsonNode request;
            try {
                request = JSON.readTree(body);
            } catch (IOException invalid) {
                respond(exchange, 400, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        problem(400, "Invalid ticket request", path, correlationId));
                return;
            }

            String withoutPrefix = path.substring("/internal/orders/".length());
            String orderId = withoutPrefix.substring(0, withoutPrefix.indexOf("/items/"));
            String orderItemId = withoutPrefix
                    .substring(withoutPrefix.indexOf("/items/") + "/items/".length(),
                            withoutPrefix.length() - "/tickets".length());

            StringBuilder tickets = new StringBuilder();
            for (JsonNode unit : request.path("tickets")) {
                if (tickets.length() > 0) {
                    tickets.append(',');
                }
                String rowLabel = unit.hasNonNull("rowLabel")
                        ? "\"" + unit.path("rowLabel").asText() + "\"" : "null";
                String seatNumber = unit.hasNonNull("seatNumber")
                        ? String.valueOf(unit.path("seatNumber").asInt()) : "null";
                tickets.append("""
                        {"ticketId":"%s","unitNumber":%d,"eventId":"%s","eventName":"%s",
                        "eventStartsAt":"%s","venueName":"%s","venueCity":"%s",
                        "sectorName":"%s","status":"ISSUED","issuedAt":"%s",
                        "qrPayload":"ty1.%s.signature","qrPngBase64":"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
                        "rowLabel":%s,"seatNumber":%s}""".formatted(
                        UUID.randomUUID(), unit.path("unitNumber").asInt(),
                        request.path("eventId").asText(),
                        request.path("eventName").asText(),
                        request.path("eventStartsAt").asText(),
                        request.path("venueName").asText(),
                        request.path("venueCity").asText(),
                        request.path("sectorName").asText(),
                        now(),
                        UUID.randomUUID().toString().replace("-", ""),
                        rowLabel, seatNumber));
            }

            String response = """
                    {"orderId":"%s","orderItemId":"%s","tickets":[%s]}"""
                    .formatted(orderId, orderItemId, tickets);
            TICKET_RESPONSES.put(path, response);
            respond(exchange, 201, MediaType.APPLICATION_JSON_VALUE, response);
        }
    }

    // --------------------------------------------------------------------- util

    private static String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }
}
