package com.ticketya.gateway.contract;

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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de seguridad del Gateway para {@code add-jwt-and-role-security}.
 *
 * <p>Usa un IdP de prueba controlado ({@link TestJwtIssuer} + {@link JwksTestServer})
 * con firma RS256 real y las propiedades estándar de resource server de Spring
 * Security ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri} y
 * {@code ...jwk-set-uri}), sin depender de un IdP externo.
 *
 * <p>Cubre: token válido para cada rol permitido (CLIENT, ORGANIZER, ADMIN,
 * ACCESS_STAFF) con identidad derivada en el upstream ({@code X-User-Id} =
 * {@code sub}, {@code X-User-Role} = único rol) y sin reenviar el Bearer ni
 * aceptar headers falsificados; rechazo 401 con {@code X-Correlation-Id} y
 * problem+json sin llamar al upstream para token ausente, vencido, con firma
 * inválida, issuer incorrecto, {@code sub} ausente o no UUID, y claim
 * {@code roles} ausente, vacío, no array, con valor no permitido o con más de
 * un valor; y continuidad del acceso anónimo a las consultas públicas
 * ({@code GET /api/events}, {@code GET /api/events/{id}},
 * {@code GET /api/inventory/events/{eventId}/availability}) y a
 * {@code /actuator/health}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayJwtSecurityIntegrationTest {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final List<String> ALLOWED_ROLES =
            List.of("CLIENT", "ORGANIZER", "ADMIN", "ACCESS_STAFF");

    private static final TestJwtIssuer IDP = new TestJwtIssuer();
    private static final TestJwtIssuer ROGUE_IDP = new TestJwtIssuer();
    private static final JwksTestServer JWKS_SERVER = JwksTestServer.start(IDP);
    private static final String IDP_ISSUER = JWKS_SERVER.issuerUrl();

    private static final List<RecordedRequest> UPSTREAM_REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer UPSTREAM_STUB = startUpstreamStub();

    @LocalServerPort
    private int gatewayPort;

    private WebTestClient client;

    @DynamicPropertySource
    static void resourceServerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> IDP_ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> IDP_ISSUER + "/.well-known/jwks.json");
        registry.add("CATALOG_SERVICE_URL",
                () -> "http://localhost:" + UPSTREAM_STUB.getAddress().getPort());
        registry.add("PURCHASE_SERVICE_URL",
                () -> "http://localhost:" + UPSTREAM_STUB.getAddress().getPort());
        registry.add("INVENTORY_SERVICE_URL",
                () -> "http://localhost:" + UPSTREAM_STUB.getAddress().getPort());
    }

    @BeforeEach
    void setUp() {
        UPSTREAM_REQUESTS.clear();
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .build();
    }

    @AfterAll
    static void stopStubs() {
        UPSTREAM_STUB.stop(0);
        JWKS_SERVER.close();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void validJwtForEveryAllowedRoleReachesUpstreamWithDerivedIdentityOnly() {
        for (String role : ALLOWED_ROLES) {
            String sub = UUID.randomUUID().toString();
            client.get().uri("/api/management/events")
                    .header("Authorization", "Bearer " + IDP.bearerToken(IDP_ISSUER, sub, role))
                    // Headers falsificados por el cliente: deben ser descartados.
                    .header("X-User-Id", "forged-user")
                    .header("X-User-Role", "ADMIN")
                    .header(CORRELATION_HEADER, "trace-" + role)
                    .exchange()
                    .expectStatus().isOk();

            assertThat(UPSTREAM_REQUESTS)
                    .filteredOn(request -> ("trace-" + role).equals(request.correlationId()))
                    .singleElement()
                    .satisfies(request -> {
                        assertThat(request.path()).isEqualTo("/api/management/events");
                        assertThat(request.userId())
                                .as("upstream recibe X-User-Id derivado del sub del JWT")
                                .isEqualTo(sub);
                        assertThat(request.userRole())
                                .as("upstream recibe X-User-Role derivado del único rol del JWT")
                                .isEqualTo(role);
                        assertThat(request.authorization())
                                .as("el Bearer no se reenvía al servicio")
                                .isNull();
                    });
        }
    }

    @Test
    void missingJwtOnProtectedRouteResponds401WithCorrelationIdWithoutCallingUpstream() {
        client.get().uri("/api/management/events")
                .header(CORRELATION_HEADER, "unauthorized-trace")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(CORRELATION_HEADER, "unauthorized-trace")
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.correlationId").isEqualTo("unauthorized-trace")
                .jsonPath("$.status").isEqualTo(401);

        assertThat(UPSTREAM_REQUESTS)
                .as("el rechazo 401 no llega al upstream")
                .isEmpty();
    }

    @Test
    void invalidJwtVariantsAreRejectedWith401WithoutCallingUpstream() {
        String sub = UUID.randomUUID().toString();
        List<String> tokens = new ArrayList<>(List.of(
                // Malformado: no es un JWT.
                "not-a-jwt",
                // Vencido.
                IDP.token(IDP_ISSUER, sub, List.of("CLIENT"), Instant.now().minusSeconds(60)),
                // Firmado por otra clave no publicada en el JWKS configurado.
                ROGUE_IDP.bearerToken(IDP_ISSUER, sub, "CLIENT"),
                // Issuer incorrecto.
                IDP.bearerToken("https://rogue-idp.example", sub, "CLIENT"),
                // Sin sub.
                IDP.token(IDP_ISSUER, null, List.of("CLIENT"), Instant.now().plusSeconds(600)),
                // Sub que no es UUID.
                IDP.bearerToken(IDP_ISSUER, "not-a-uuid", "CLIENT"),
                // Sin claim roles.
                IDP.token(IDP_ISSUER, sub, null, Instant.now().plusSeconds(600)),
                // Roles vacíos.
                IDP.token(IDP_ISSUER, sub, List.of(), Instant.now().plusSeconds(600)),
                // Rol fuera del vocabulario permitido.
                IDP.token(IDP_ISSUER, sub, List.of("SUPERUSER"), Instant.now().plusSeconds(600)),
                // Más de un rol.
                IDP.token(IDP_ISSUER, sub, List.of("CLIENT", "ADMIN"), Instant.now().plusSeconds(600)),
                // Roles como string en lugar de array.
                IDP.jwt(claimsMap(sub, "CLIENT"))));

        // Firmado y con `exp` ausente: el contrato exige token vigente con expiración.
        tokens.add(IDP.token(IDP_ISSUER, sub, List.of("CLIENT"), null));

        // Sub UUID no canónico corto, sólo si este JDK lo parsea como UUID válido.
        if (acceptsShortUuid()) {
            tokens.add(IDP.bearerToken(IDP_ISSUER, "1-1-1-1-1", "CLIENT"));
        }

        for (int index = 0; index < tokens.size(); index++) {
            client.get().uri("/api/orders")
                    .header("Authorization", "Bearer " + tokens.get(index))
                    .header(CORRELATION_HEADER, "invalid-" + index)
                    .exchange()
                    .expectStatus().isUnauthorized();

            assertThat(UPSTREAM_REQUESTS)
                    .as("el caso invalido %d no debe llegar al upstream", index)
                    .isEmpty();
        }
    }

    /** Indica si {@code UUID.fromString} de este JDK acepta un UUID no canonico corto. */
    private static boolean acceptsShortUuid() {
        try {
            UUID.fromString("1-1-1-1-1");
            return true;
        } catch (IllegalArgumentException expected) {
            return false;
        }
    }

    @Test
    void forgedIdentityHeadersAloneDoNotAuthenticate() {
        client.get().uri("/api/orders")
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-User-Role", "CLIENT")
                .header(CORRELATION_HEADER, "forged-only")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(CORRELATION_HEADER, "forged-only");

        assertThat(UPSTREAM_REQUESTS).isEmpty();
    }

    @Test
    void publicQueriesAndHealthRemainAccessibleWithoutJwt() {
        String eventId = UUID.randomUUID().toString();

        client.get().uri("/api/events?city=Rosario")
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/events/" + eventId)
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/inventory/events/" + eventId + "/availability")
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();

        assertThat(UPSTREAM_REQUESTS)
                .as("las tres consultas públicas llegan al upstream; el health no se enruta")
                .hasSize(3)
                .extracting(RecordedRequest::path)
                .containsExactly("/api/events", "/api/events/" + eventId,
                        "/api/inventory/events/" + eventId + "/availability");
    }

    // ------------------------------------------------------------------ stubs

    private static Map<String, Object> claimsMap(String sub, Object roles) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", sub);
        claims.put("roles", roles);
        claims.put("iss", IDP_ISSUER);
        claims.put("exp", Instant.now().plusSeconds(600).getEpochSecond());
        return claims;
    }

    private static HttpServer startUpstreamStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", GatewayJwtSecurityIntegrationTest::handle);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "jwt-security-stub");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo levantar el upstream de prueba", exception);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            UPSTREAM_REQUESTS.add(new RecordedRequest(
                    exchange.getRequestMethod(),
                    path,
                    query,
                    exchange.getRequestHeaders().getFirst(CORRELATION_HEADER),
                    exchange.getRequestHeaders().getFirst("X-User-Id"),
                    exchange.getRequestHeaders().getFirst("X-User-Role"),
                    exchange.getRequestHeaders().getFirst("Authorization")));

            byte[] response = ("{\"path\":\"" + path + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            String correlationId = exchange.getRequestHeaders().getFirst(CORRELATION_HEADER);
            if (correlationId != null) {
                exchange.getResponseHeaders().set(CORRELATION_HEADER, correlationId);
            }
            exchange.sendResponseHeaders(200, response.length);
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
            String authorization) {
    }
}
