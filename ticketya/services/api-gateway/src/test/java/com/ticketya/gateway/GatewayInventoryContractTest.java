package com.ticketya.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de enrutamiento del Gateway para Inventory: precedencia de
 * `/api/inventory/**` sobre `/api/**`, no publicación de `/internal/**`, preservación de
 * errores problem+json y propagación del correlation ID.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayInventoryContractTest {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private static final UUID INVENTORY_404_EVENT =
            UUID.fromString("00000000-0000-4000-8000-000000000404");
    private static final UUID INVENTORY_409_EVENT =
            UUID.fromString("00000000-0000-4000-8000-000000000409");
    private static final UUID INVENTORY_503_EVENT =
            UUID.fromString("00000000-0000-4000-8000-000000000503");

    private static final List<RecordedRequest> INVENTORY_REQUESTS = new CopyOnWriteArrayList<>();
    private static final List<RecordedRequest> CATALOG_REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer INVENTORY_STUB =
            startStub("inventory-contract-stub", INVENTORY_REQUESTS, true);
    private static final HttpServer CATALOG_STUB =
            startStub("catalog-contract-stub", CATALOG_REQUESTS, false);

    @LocalServerPort
    private int gatewayPort;

    private WebTestClient client;

    @AfterAll
    static void stopStubs() {
        INVENTORY_STUB.stop(0);
        CATALOG_STUB.stop(0);
    }

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        registry.add("INVENTORY_SERVICE_URL",
                () -> "http://localhost:" + INVENTORY_STUB.getAddress().getPort());
        registry.add("CATALOG_SERVICE_URL",
                () -> "http://localhost:" + CATALOG_STUB.getAddress().getPort());
    }

    @BeforeEach
    void setUp() {
        INVENTORY_REQUESTS.clear();
        CATALOG_REQUESTS.clear();
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    // ------------------------------------------------------------------------ tests

    @Test
    void routesAvailabilityToInventoryPreservingPathMethodAndQuery() {
        UUID eventId = UUID.randomUUID();
        String path = "/api/inventory/events/" + eventId + "/availability";

        client.get().uri(path + "?seat=A&row=B")
                .exchange()
                .expectStatus().isOk();

        assertThat(INVENTORY_REQUESTS).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo(path);
            assertThat(request.query()).isEqualTo("seat=A&row=B");
        });
        assertThat(CATALOG_REQUESTS)
                .as("la ruta de inventario no llega a Catalog")
                .isEmpty();
    }

    @Test
    void keepsCatalogRoutesGoingToCatalog() {
        client.get().uri("/api/events?city=Rosario&page=0")
                .exchange()
                .expectStatus().isOk();

        assertThat(CATALOG_REQUESTS).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/api/events");
            assertThat(request.query()).isEqualTo("city=Rosario&page=0");
        });
        assertThat(INVENTORY_REQUESTS).isEmpty();
    }

    @Test
    void doesNotExposeInternalReservationCommandsThroughTheGateway() {
        UUID eventId = UUID.randomUUID();
        String correlation = "trace-internal";

        client.post().uri("/internal/reservations")
                .header(CORRELATION_HEADER, correlation)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\":\"" + UUID.randomUUID() + "\"}")
                .exchange()
                .expectStatus().isNotFound();

        client.get().uri("/internal/reservations/" + UUID.randomUUID())
                .header(CORRELATION_HEADER, correlation)
                .exchange()
                .expectStatus().isNotFound();

        client.get().uri("/internal/reservations/by-order/" + UUID.randomUUID())
                .header(CORRELATION_HEADER, correlation)
                .exchange()
                .expectStatus().isNotFound();

        client.put().uri("/internal/events/" + eventId + "/hold-ttl")
                .header(CORRELATION_HEADER, correlation)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"ttlSeconds\":300}")
                .exchange()
                .expectStatus().isNotFound();

        assertThat(INVENTORY_REQUESTS)
                .as("ningún comando interno llega a Inventory")
                .isEmpty();
        assertThat(CATALOG_REQUESTS)
                .as("ningún comando interno llega a Catalog")
                .isEmpty();
    }

    @Test
    void passesProblemJsonErrorsFromInventoryIntact() {
        assertProblemPassthrough(INVENTORY_404_EVENT, 404, "Not found");
        assertProblemPassthrough(INVENTORY_409_EVENT, 409, "Conflict");
        assertProblemPassthrough(INVENTORY_503_EVENT, 503, "Service unavailable");
    }

    @Test
    void propagatesSuppliedCorrelationIdToInventoryAndBackToTheClient() {
        String path = "/api/inventory/events/" + UUID.randomUUID() + "/availability";

        client.get().uri(path)
                .header(CORRELATION_HEADER, "trace-inventory-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values(CORRELATION_HEADER, values ->
                        assertThat(values).hasSize(1).containsExactly("trace-inventory-1"))
                .expectBody().returnResult();

        assertThat(INVENTORY_REQUESTS).singleElement().satisfies(request ->
                assertThat(request.correlationId()).isEqualTo("trace-inventory-1"));
        assertThat(CATALOG_REQUESTS).isEmpty();
    }

    @Test
    void generatesCorrelationIdWhenAbsentAndPropagatesItToInventory() {
        String path = "/api/inventory/events/" + UUID.randomUUID() + "/availability";

        String responseCorrelationId = client.get().uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values(CORRELATION_HEADER, values ->
                        assertThat(values).hasSize(1))
                .expectBody().returnResult()
                .getResponseHeaders().getFirst(CORRELATION_HEADER);

        assertThat(responseCorrelationId).isNotBlank();
        assertThat(INVENTORY_REQUESTS).singleElement().satisfies(request ->
                assertThat(request.correlationId()).isEqualTo(responseCorrelationId));
        assertThat(CATALOG_REQUESTS).isEmpty();
    }

    // ------------------------------------------------------------------------ utils

    private void assertProblemPassthrough(UUID eventId, int status, String title) {
        String correlationId = "trace-" + status;
        String path = "/api/inventory/events/" + eventId + "/availability";
        String expectedBody = "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,"
                + "\"detail\":\"Inventory refused the request\",\"instance\":\"%s\","
                + "\"correlationId\":\"%s\"}"
                .formatted(title, status, path, correlationId);

        client.get().uri(path)
                .header(CORRELATION_HEADER, correlationId)
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals(CORRELATION_HEADER, correlationId)
                .expectBody(String.class).isEqualTo(expectedBody);

        assertThat(INVENTORY_REQUESTS)
                .filteredOn(request -> request.path().equals(path))
                .singleElement()
                .satisfies(request -> assertThat(request.correlationId())
                        .isEqualTo(correlationId));
        assertThat(CATALOG_REQUESTS).isEmpty();
    }

    private static HttpServer startStub(String threadName, List<RecordedRequest> recorder,
                                        boolean inventoryBehaviour) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                if (inventoryBehaviour) {
                    handleInventory(exchange, recorder);
                } else {
                    handleCatalog(exchange, recorder);
                }
            });
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, threadName);
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void handleInventory(HttpExchange exchange, List<RecordedRequest> recorder)
            throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
            recorder.add(new RecordedRequest(
                    exchange.getRequestMethod(), path, query, correlationId));

            if (path.contains(INVENTORY_404_EVENT.toString())) {
                respond(exchange, 404, problem(404, "Not found", path, correlationId));
                return;
            }
            if (path.contains(INVENTORY_409_EVENT.toString())) {
                respond(exchange, 409, problem(409, "Conflict", path, correlationId));
                return;
            }
            if (path.contains(INVENTORY_503_EVENT.toString())) {
                respond(exchange, 503,
                        problem(503, "Service unavailable", path, correlationId));
                return;
            }
            respond(exchange, 200, "{\"path\":\"" + path + "\"}");
        }
    }

    private static void handleCatalog(HttpExchange exchange, List<RecordedRequest> recorder)
            throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
            recorder.add(new RecordedRequest(
                    exchange.getRequestMethod(), path, query, correlationId));
            respond(exchange, 200, "{\"path\":\"" + path + "\"}");
        }
    }

    private static String problem(int status, String title, String instance,
                                  String correlationId) {
        return "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,"
                + "\"detail\":\"Inventory refused the request\",\"instance\":\"%s\","
                + "\"correlationId\":\"%s\"}"
                .formatted(title, status, instance, correlationId == null ? "" : correlationId);
    }

    private static void respond(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String contentType = status >= 400
                ? MediaType.APPLICATION_PROBLEM_JSON_VALUE
                : MediaType.APPLICATION_JSON_VALUE;
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

    private record RecordedRequest(String method, String path, String query,
                                   String correlationId) {
    }
}
