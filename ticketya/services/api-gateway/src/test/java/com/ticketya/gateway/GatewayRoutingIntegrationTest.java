package com.ticketya.gateway;

import com.ticketya.gateway.contract.JwksTestServer;
import com.ticketya.gateway.contract.TestJwtIssuer;
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

/**
 * Enrutamiento del Gateway hacia Catalog: preservacion de path/metodo/query/body,
 * precedencia de rutas, correlation ID y passthrough de problem+json. Desde
 * {@code add-jwt-and-role-security}, las operaciones protegidas envian un JWT
 * RS256 real emitido por un IdP de prueba ({@link TestJwtIssuer} con JWKS propio)
 * y la identidad del upstream debe derivarse de sus claims, no de headers del
 * cliente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final TestJwtIssuer IDP = new TestJwtIssuer();
    private static final JwksTestServer JWKS_SERVER = JwksTestServer.start(IDP);
    private static final String IDP_ISSUER = JWKS_SERVER.issuerUrl();
    private static final List<RecordedRequest> RECORDED = new CopyOnWriteArrayList<>();
    private static final List<RecordedRequest> QUEUE_RECORDED = new CopyOnWriteArrayList<>();
    private static final HttpServer CATALOG_STUB = startStub(RECORDED, "catalog-stub");
    private static final HttpServer QUEUE_STUB = startStub(QUEUE_RECORDED, "queue-stub");

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private RouteLocator routeLocator;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        RECORDED.clear();
        QUEUE_RECORDED.clear();
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    @AfterAll
    static void stopStubs() {
        CATALOG_STUB.stop(0);
        QUEUE_STUB.stop(0);
        JWKS_SERVER.close();
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("CATALOG_SERVICE_URL", () -> "http://localhost:" + CATALOG_STUB.getAddress().getPort());
        registry.add("QUEUE_SERVICE_URL", () -> "http://localhost:" + QUEUE_STUB.getAddress().getPort());
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> IDP_ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> IDP_ISSUER + "/.well-known/jwks.json");
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
    void routesManagementQueryWithIdentityDerivedFromJwt() {
        String sub = UUID.randomUUID().toString();
        client.get().uri("/api/management/events")
                .header("Authorization", "Bearer " + IDP.bearerToken(IDP_ISSUER, sub, "ORGANIZER"))
                // Headers falsificados: el Gateway debe descartarlos y derivar del JWT.
                .header("X-User-Id", "forged-user")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isOk();

        assertThat(RECORDED).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/api/management/events");
            assertThat(request.userId()).as("X-User-Id derivado del sub del JWT").isEqualTo(sub);
            assertThat(request.userRole()).as("X-User-Role derivado del rol del JWT")
                    .isEqualTo("ORGANIZER");
            assertThat(request.authorization()).as("el Bearer no se reenvia al servicio")
                    .isNull();
        });
    }

    @Test
    void routesPostRequestWithoutRewritingMethodOrBody() {
        client.post().uri("/api/events")
                .header("Authorization", "Bearer " + IDP.bearerToken(
                        IDP_ISSUER, UUID.randomUUID().toString(), "ADMIN"))
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
    void routeUrisComeFromServiceUrlProperties() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();

        assertThat(routes)
                .filteredOn(route -> "catalog-service".equals(route.getId()))
                .singleElement()
                .satisfies(route -> {
                    assertThat(route.getUri().getScheme()).isEqualTo("http");
                    assertThat(route.getUri().getHost()).isEqualTo("localhost");
                    assertThat(route.getUri().getPort())
                            .isEqualTo(CATALOG_STUB.getAddress().getPort());
                });

        assertThat(routes)
                .filteredOn(route -> "queue-service".equals(route.getId()))
                .singleElement()
                .satisfies(route -> assertThat(route.getUri().getPort())
                        .isEqualTo(QUEUE_STUB.getAddress().getPort()));

        assertThat(routes)
                .filteredOn(route -> "inventory-service".equals(route.getId()))
                .singleElement()
                .satisfies(route -> assertThat(route.getUri().toString())
                        .isEqualTo("http://localhost:8082"));
    }

    @Test
    void routesQueueRequestsToQueueServiceInsteadOfTheCatalogCatchAll() {
        String sub = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();

        client.post().uri("/api/queue/events/" + eventId + "/entries")
                .header("Authorization", "Bearer " + IDP.bearerToken(IDP_ISSUER, sub, "CLIENT"))
                .header("X-Admission-Token", "admission-token-1")
                .header(CORRELATION_HEADER, "queue-trace")
                .exchange()
                .expectStatus().isOk();

        assertThat(RECORDED)
                .as("la ruta /api/queue/** no debe caer en el catch-all de Catalog")
                .isEmpty();
        assertThat(QUEUE_RECORDED).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/api/queue/events/" + eventId + "/entries");
            assertThat(request.correlationId()).isEqualTo("queue-trace");
            assertThat(request.userId()).as("X-User-Id derivado del sub del JWT").isEqualTo(sub);
            assertThat(request.userRole()).isEqualTo("CLIENT");
            assertThat(request.authorization()).as("el Bearer no se reenvía al servicio").isNull();
            assertThat(request.admissionToken())
                    .as("X-Admission-Token no es un encabezado de identidad: se propaga tal cual")
                    .isEqualTo("admission-token-1");
        });
    }

    @Test
    void queueRouteRequiresJwtAndDoesNotReachTheService() {
        client.get().uri("/api/queue/events/" + UUID.randomUUID() + "/entries/me")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(QUEUE_RECORDED)
                .as("el rechazo 401 no llega a queue-service")
                .isEmpty();
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
    void protectedRouteWithoutJwtResponds401WithCorrelationIdWithoutCallingCatalog() {
        client.get().uri("/api/management/events")
                .header(CORRELATION_HEADER, "unauthorized-trace")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(CORRELATION_HEADER, "unauthorized-trace");

        assertThat(RECORDED)
                .as("el rechazo 401 no llega al upstream")
                .isEmpty();
    }

    @Test
    void passesThroughProblemJsonErrorsFromCatalog() {
        String correlationId = "domain-trace";
        String expectedBody = "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                + "\"instance\":\"/api/problem\",\"correlationId\":\"domain-trace\"}";

        client.get().uri("/api/problem")
                .header("Authorization", "Bearer " + IDP.bearerToken(
                        IDP_ISSUER, UUID.randomUUID().toString(), "CLIENT"))
                .header(CORRELATION_HEADER, correlationId)
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals(CORRELATION_HEADER, correlationId)
                .expectBody(String.class).isEqualTo(expectedBody);

        assertThat(RECORDED).singleElement()
                .satisfies(request -> assertThat(request.correlationId()).isEqualTo(correlationId));
    }

    private static HttpServer startStub(List<RecordedRequest> sink, String threadName) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> handle(exchange, sink));
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

    private static void handle(HttpExchange exchange, List<RecordedRequest> sink) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
            sink.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    path,
                    query,
                    correlationId,
                    exchange.getRequestHeaders().getFirst("X-User-Id"),
                    exchange.getRequestHeaders().getFirst("X-User-Role"),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-Admission-Token"),
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
            String authorization,
            String admissionToken,
            String body) {
    }
}
