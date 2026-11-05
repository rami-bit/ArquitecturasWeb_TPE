package com.ticketya.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final List<RecordedRequest> RECORDED = new CopyOnWriteArrayList<>();
    private static final HttpServer CATALOG_STUB = startStub();

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private RouteLocator routeLocator;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        RECORDED.clear();
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    @AfterAll
    static void stopStub() {
        CATALOG_STUB.stop(0);
    }

    @DynamicPropertySource
    static void catalogUrl(DynamicPropertyRegistry registry) {
        registry.add("CATALOG_SERVICE_URL", () -> "http://localhost:" + CATALOG_STUB.getAddress().getPort());
    }

    @Test
    void routesPublicQueryPreservingPathMethodAndQueryParameters() {
        client.get().uri("/api/events?city=Rosario&page=0&size=5")
                .exchange()
                .expectStatus().isOk();

        assertThat(RECORDED).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/api/events");
            assertThat(request.query()).isEqualTo("city=Rosario&page=0&size=5");
            assertThat(request.userId()).isNull();
            assertThat(request.userRole()).isNull();
        });
    }

    @Test
    void routesManagementQueryWithIdentityHeaders() {
        client.get().uri("/api/management/events")
                .header("X-User-Id", "organizer-1")
                .header("X-User-Role", "ORGANIZER")
                .exchange()
                .expectStatus().isOk();

        assertThat(RECORDED).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/api/management/events");
            assertThat(request.userId()).isEqualTo("organizer-1");
            assertThat(request.userRole()).isEqualTo("ORGANIZER");
        });
    }

    @Test
    void routesPostRequestWithoutRewritingMethodOrBody() {
        client.post().uri("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Festival\"}")
                .exchange()
                .expectStatus().isOk();

        assertThat(RECORDED).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/api/events");
            assertThat(request.body()).isEqualTo("{\"name\":\"Festival\"}");
        });
    }

    @Test
    void doesNotForwardActuatorRequestsToCatalog() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();

        assertThat(RECORDED).isEmpty();
    }

    @Test
    void doesNotForwardRoutesOutsideApiPrefix() {
        client.get().uri("/not-a-route")
                .exchange()
                .expectStatus().isNotFound();

        assertThat(RECORDED).isEmpty();
    }

    @Test
    void routeUriComesFromCatalogServiceUrlProperty() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).singleElement().satisfies(route -> {
            assertThat(route.getId()).isEqualTo("catalog-service");
            assertThat(route.getUri().getScheme()).isEqualTo("http");
            assertThat(route.getUri().getHost()).isEqualTo("localhost");
            assertThat(route.getUri().getPort()).isEqualTo(CATALOG_STUB.getAddress().getPort());
        });
    }

    @Test
    void propagatesSuppliedCorrelationIdToCatalogAndReturnsSingleValue() {
        client.get().uri("/api/events")
                .header(CORRELATION_HEADER, "trace-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values(CORRELATION_HEADER, values ->
                        assertThat(values).hasSize(1).containsExactly("trace-123"))
                .expectBody().returnResult();

        assertThat(RECORDED).singleElement()
                .satisfies(request -> assertThat(request.correlationId()).isEqualTo("trace-123"));
    }

    @Test
    void generatesCorrelationIdWhenMissingAndUsesItEndToEnd() {
        String correlationId = client.get().uri("/api/events")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().values(CORRELATION_HEADER, values ->
                        assertThat(values).hasSize(1))
                .expectBody().returnResult()
                .getResponseHeaders().getFirst(CORRELATION_HEADER);

        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId).version()).isEqualTo(4);
        assertThat(RECORDED).singleElement()
                .satisfies(request -> assertThat(request.correlationId()).isEqualTo(correlationId));
    }

    @Test
    void generatesCorrelationIdWhenBlankAndUsesItEndToEnd() {
        String correlationId = client.get().uri("/api/events")
                .header(CORRELATION_HEADER, "")
                .exchange()
                .expectStatus().isOk()
                .expectBody().returnResult()
                .getResponseHeaders().getFirst(CORRELATION_HEADER);

        assertThat(correlationId).isNotBlank();
        assertThat(UUID.fromString(correlationId).version()).isEqualTo(4);
        assertThat(RECORDED).singleElement()
                .satisfies(request -> assertThat(request.correlationId()).isEqualTo(correlationId));
    }

    @Test
    void includesCorrelationIdOnGatewayOwnNotFoundResponse() {
        client.get().uri("/not-a-route")
                .header(CORRELATION_HEADER, "not-found-trace")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals(CORRELATION_HEADER, "not-found-trace");

        assertThat(RECORDED).isEmpty();
    }

    @Test
    void doesNotInjectIdentityHeadersThatClientDidNotSend() {
        client.get().uri("/api/management/events")
                .exchange()
                .expectStatus().isOk();

        assertThat(RECORDED).singleElement().satisfies(request -> {
            assertThat(request.userId()).isNull();
            assertThat(request.userRole()).isNull();
        });
    }

    @Test
    void passesThroughProblemJsonErrorsFromCatalog() {
        String correlationId = "domain-trace";
        String expectedBody = "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                + "\"instance\":\"/api/problem\",\"correlationId\":\"domain-trace\"}";

        client.get().uri("/api/problem")
                .header(CORRELATION_HEADER, correlationId)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals(CORRELATION_HEADER, correlationId)
                .expectBody(String.class).isEqualTo(expectedBody);

        assertThat(RECORDED).singleElement()
                .satisfies(request -> assertThat(request.correlationId()).isEqualTo(correlationId));
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", GatewayRoutingIntegrationTest::handle);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "catalog-stub");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
            RECORDED.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    path,
                    query,
                    correlationId,
                    exchange.getRequestHeaders().getFirst("X-User-Id"),
                    exchange.getRequestHeaders().getFirst("X-User-Role"),
                    body));

            String contentType = MediaType.APPLICATION_JSON_VALUE;
            int status = 200;
            String responseBody = "{\"path\":\"" + path + "\"}";
            if ("/api/problem".equals(path)) {
                status = 403;
                contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE;
                responseBody = "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                        + "\"instance\":\"/api/problem\",\"correlationId\":\""
                        + (correlationId == null ? "" : correlationId) + "\"}";
            }

            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            if (correlationId != null) {
                exchange.getResponseHeaders().set(CORRELATION_HEADER, correlationId);
            }
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        }
    }

    private record RecordedRequest(
            String method,
            String path,
            String query,
            String correlationId,
            String userId,
            String userRole,
            String body) {
    }
}
