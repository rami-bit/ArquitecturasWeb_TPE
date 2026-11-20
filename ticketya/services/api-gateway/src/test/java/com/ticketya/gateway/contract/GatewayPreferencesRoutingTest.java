package com.ticketya.gateway.contract;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** PDF-F10: precedencia de la ruta de preferencias, rol CLIENT y no publicación de /internal. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayPreferencesRoutingTest {

    private static final TestJwtIssuer IDP = new TestJwtIssuer();
    private static final JwksTestServer JWKS = JwksTestServer.start(IDP);
    private static final String ISSUER = JWKS.issuerUrl();
    private static final List<Recorded> REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer PREFERENCES = stub();
    private static final HttpServer CATALOG = stub();

    @LocalServerPort
    private int port;

    @Autowired
    private RouteLocator routeLocator;

    private WebTestClient client;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> ISSUER + "/.well-known/jwks.json");
        registry.add("GATEWAY_PREFERENCES_SERVICE_URL", () -> url(PREFERENCES));
        registry.add("CATALOG_SERVICE_URL", () -> url(CATALOG));
    }

    @BeforeEach
    void setUp() {
        REQUESTS.clear();
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterAll
    static void stop() {
        PREFERENCES.stop(0);
        CATALOG.stop(0);
        JWKS.close();
    }

    @Test
    void preferencesRoutePrecedesTheCatalogCatchAll() {
        List<String> ids = routeLocator.getRoutes().map(Route::getId).collectList().block();
        assertThat(ids).contains("preferences-service", "catalog-service");
        assertThat(ids.indexOf("preferences-service")).isLessThan(ids.indexOf("catalog-service"));
    }

    @Test
    void clientIsRoutedToPreferencesWithIdentityDerivedFromJwt() {
        UUID customerId = UUID.randomUUID();
        client.get().uri("/api/customers/me/preferences")
                .header("Authorization", "Bearer " + IDP.bearerToken(ISSUER, customerId.toString(), "CLIENT"))
                .header("X-User-Id", "forged-user")
                .header("X-User-Role", "ADMIN")
                .header("X-Correlation-Id", "trace-preferences-gateway")
                .exchange()
                .expectStatus().isOk();

        assertThat(REQUESTS).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/api/customers/me/preferences");
            assertThat(request.userId()).isEqualTo(customerId.toString());
            assertThat(request.userRole()).isEqualTo("CLIENT");
            assertThat(request.correlationId()).isEqualTo("trace-preferences-gateway");
        });
    }

    @Test
    void otherRolesAreRejectedAndAnonymousGets401() {
        client.get().uri("/api/customers/me/recommendations")
                .header("Authorization", "Bearer " + IDP.bearerToken(
                        ISSUER, UUID.randomUUID().toString(), "ORGANIZER"))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        client.get().uri("/api/customers/me/preferences")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void internalRecommendationContractsAreNotPublished() {
        client.get().uri("/internal/events/recommendation-candidates")
                .exchange()
                .expectStatus().isNotFound();
        client.get().uri("/internal/customers/" + UUID.randomUUID() + "/recommendation-signals")
                .exchange()
                .expectStatus().isNotFound();
        assertThat(REQUESTS).isEmpty();
    }

    private static HttpServer stub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", GatewayPreferencesRoutingTest::record);
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void record(HttpExchange exchange) throws IOException {
        REQUESTS.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("X-User-Id"),
                exchange.getRequestHeaders().getFirst("X-User-Role"),
                exchange.getRequestHeaders().getFirst("X-Correlation-Id")));
        byte[] body = "{\"version\":0}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private record Recorded(String method, String path, String userId, String userRole,
                            String correlationId) {
    }
}
