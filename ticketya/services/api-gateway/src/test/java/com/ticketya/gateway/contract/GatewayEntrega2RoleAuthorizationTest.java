package com.ticketya.gateway.contract;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seguridad y enrutamiento de las rutas de Entrega 2 incorporadas por
 * E2-02 (bitácora), E2-03 (control de acceso QR), E2-04 (reportes) y PDF-F11
 * (bandeja de notificaciones del cliente):
 *
 * <ul>
 *   <li>cada ruta llega al servicio correcto con la identidad derivada;</li>
 *   <li>sólo los roles autorizados la alcanzan; el resto recibe {@code 403}
 *       problem+json sin invocar el upstream;</li>
 *   <li>sin token responde {@code 401};</li>
 *   <li>el Gateway registra cada solicitud con endpoint, usuario, rol,
 *       código de respuesta, duración y correlation id, incluidos los 401/403.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class GatewayEntrega2RoleAuthorizationTest {

    private static final List<String> ROLES = List.of("CLIENT", "ORGANIZER", "ADMIN", "ACCESS_STAFF");
    private static final TestJwtIssuer IDP = new TestJwtIssuer();
    private static final JwksTestServer JWKS = JwksTestServer.start(IDP);
    private static final String ISSUER = JWKS.issuerUrl();

    private static final List<Recorded> REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer TICKET = stub("ticket");
    private static final HttpServer PURCHASE = stub("purchase");
    private static final HttpServer AUDIT = stub("audit");
    private static final HttpServer CATALOG = stub("catalog");
    private static final HttpServer NOTIFICATION = stub("notification");

    private record Route(HttpMethod method, String path, String upstream, Set<String> allowedRoles) {
    }

    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final List<Route> ROUTES = List.of(
            new Route(HttpMethod.POST, "/api/access/events/" + EVENT_ID + "/validations", "ticket",
                    Set.of("ACCESS_STAFF")),
            new Route(HttpMethod.GET, "/api/access/events/" + EVENT_ID + "/scans", "ticket",
                    Set.of("ACCESS_STAFF", "ADMIN")),
            new Route(HttpMethod.GET, "/api/reports/events/" + EVENT_ID, "purchase",
                    Set.of("ORGANIZER", "ADMIN")),
            new Route(HttpMethod.GET, "/api/audit/events", "audit", Set.of("ADMIN")),
            new Route(HttpMethod.GET, "/api/audit/traces/trace-1", "audit", Set.of("ADMIN")),
            new Route(HttpMethod.GET, "/api/notifications/me", "notification", Set.of("CLIENT")));

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> ISSUER + "/.well-known/jwks.json");
        registry.add("TICKET_SERVICE_URL", () -> url(TICKET));
        registry.add("PURCHASE_SERVICE_URL", () -> url(PURCHASE));
        registry.add("AUDIT_SERVICE_URL", () -> url(AUDIT));
        registry.add("CATALOG_SERVICE_URL", () -> url(CATALOG));
        registry.add("NOTIFICATION_SERVICE_URL", () -> url(NOTIFICATION));
        registry.add("INVENTORY_SERVICE_URL", () -> url(CATALOG));
    }

    @BeforeEach
    void setUp() {
        REQUESTS.clear();
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterAll
    static void stop() {
        TICKET.stop(0);
        PURCHASE.stop(0);
        AUDIT.stop(0);
        CATALOG.stop(0);
        NOTIFICATION.stop(0);
        JWKS.close();
    }

    @Test
    void authorizedRolesReachTheRightServiceWithDerivedIdentity() {
        for (Route route : ROUTES) {
            for (String role : route.allowedRoles()) {
                REQUESTS.clear();
                String sub = UUID.randomUUID().toString();
                exchange(route, IDP.bearerToken(ISSUER, sub, role), "ok-" + role)
                        .expectStatus().isOk();
                assertThat(REQUESTS)
                        .as("%s %s con rol %s llega a %s", route.method(), route.path(), role, route.upstream())
                        .singleElement()
                        .satisfies(request -> {
                            assertThat(request.upstream()).isEqualTo(route.upstream());
                            assertThat(request.path()).isEqualTo(route.path().split("\\?")[0]);
                            assertThat(request.userId()).isEqualTo(sub);
                            assertThat(request.role()).isEqualTo(role);
                            assertThat(request.authorization()).isNull();
                            assertThat(request.correlationId()).isEqualTo("ok-" + role);
                        });
            }
        }
    }

    @Test
    void unauthorizedRolesReceive403ProblemWithoutReachingUpstream() {
        for (Route route : ROUTES) {
            for (String role : ROLES) {
                if (route.allowedRoles().contains(role)) {
                    continue;
                }
                exchange(route, IDP.bearerToken(ISSUER, UUID.randomUUID().toString(), role), "denied-" + role)
                        .expectStatus().isForbidden()
                        .expectHeader().valueEquals("X-Correlation-Id", "denied-" + role)
                        .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                        .expectBody()
                        .jsonPath("$.status").isEqualTo(403)
                        .jsonPath("$.correlationId").isEqualTo("denied-" + role);
            }
        }
        assertThat(REQUESTS).as("ningún rol no autorizado llega a un servicio").isEmpty();
    }

    @Test
    void missingTokenResponds401() {
        for (Route route : ROUTES) {
            exchange(route, null, "anonymous").expectStatus().isUnauthorized();
        }
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void gatewayLogsEveryRequestWithEndpointUserStatusDurationAndCorrelationId(CapturedOutput output) {
        String admin = UUID.randomUUID().toString();
        String client = UUID.randomUUID().toString();
        exchange(ROUTES.get(3), IDP.bearerToken(ISSUER, admin, "ADMIN"), "log-ok").expectStatus().isOk();
        exchange(ROUTES.get(3), IDP.bearerToken(ISSUER, client, "CLIENT"), "log-403").expectStatus().isForbidden();
        exchange(ROUTES.get(3), null, "log-401").expectStatus().isUnauthorized();

        // La línea se escribe al completar el exchange (doFinally), que puede ocurrir
        // instantes después de que el cliente recibe la respuesta.
        awaitLog(output, "http method=GET path=/api/audit/events status=200 durationMs=\\d+ "
                + "userId=" + admin + " role=ADMIN correlationId=log-ok");
        awaitLog(output, "http method=GET path=/api/audit/events status=403 durationMs=\\d+ "
                + "userId=" + client + " role=CLIENT correlationId=log-403");
        awaitLog(output, "http method=GET path=/api/audit/events status=401 durationMs=\\d+ "
                + "userId=- role=- correlationId=log-401");
    }

    private static void awaitLog(CapturedOutput output, String regex) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        long deadline = System.currentTimeMillis() + 5_000;
        while (!pattern.matcher(output.getOut()).find() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(output.getOut()).containsPattern(regex);
    }

    // ------------------------------------------------------------------ helpers

    private WebTestClient.ResponseSpec exchange(Route route, String token, String correlationId) {
        WebTestClient.RequestBodySpec spec = client.method(route.method()).uri(route.path())
                .header("X-Correlation-Id", correlationId)
                .header("X-User-Id", "forged")
                .header("X-User-Role", "ADMIN");
        if (token != null) {
            spec = spec.header("Authorization", "Bearer " + token);
        }
        if (route.method() == HttpMethod.POST) {
            return spec.contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("qrPayload", "v1.x.y")).exchange();
        }
        return spec.exchange();
    }

    private static String url(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static HttpServer stub(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> handle(name, exchange));
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "e2-stub-" + name);
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void handle(String name, HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            REQUESTS.add(new Recorded(name, exchange.getRequestURI().getRawPath(),
                    exchange.getRequestHeaders().getFirst("X-User-Id"),
                    exchange.getRequestHeaders().getFirst("X-User-Role"),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-Correlation-Id")));
            byte[] body = ("{\"upstream\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private record Recorded(String upstream, String path, String userId, String role, String authorization,
                            String correlationId) {
    }
}
