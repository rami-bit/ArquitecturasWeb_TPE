package com.ticketya.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayPurchaseContractTest {
    private static final String CORRELATION = "X-Correlation-Id";
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer PURCHASE_STUB = startStub();

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private RouteLocator routeLocator;

    private WebTestClient client;

    @DynamicPropertySource
    static void purchaseUrl(DynamicPropertyRegistry registry) {
        registry.add("PURCHASE_SERVICE_URL",
                () -> "http://localhost:" + PURCHASE_STUB.getAddress().getPort());
    }

    @BeforeEach
    void setUp() {
        REQUESTS.clear();
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + gatewayPort).build();
    }

    @AfterAll
    static void stopStub() {
        PURCHASE_STUB.stop(0);
    }

    @Test
    void routesPurchasePreservingIdentityAndCorrelation() {
        client.post().uri("/api/purchases?source=demo")
                .header("X-User-Id", "client-1")
                .header("X-User-Role", "CLIENT")
                .header(CORRELATION, "purchase-trace")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"event-1\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CORRELATION, "purchase-trace")
                .expectBody().jsonPath("$.path").isEqualTo("/api/purchases");

        assertThat(REQUESTS).containsExactly("POST /api/purchases?source=demo "
                + "client-1 CLIENT purchase-trace {\"eventId\":\"event-1\"}");
    }

    @Test
    void keepsPurchaseRouteAheadOfCatalogCatchAll() {
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        assertThat(routes).filteredOn(route -> "purchase-service".equals(route.getId()))
                .singleElement()
                .satisfies(route -> assertThat(route.getUri().getPort())
                        .isEqualTo(PURCHASE_STUB.getAddress().getPort()));
    }

    @Test
    void doesNotRouteInternalPaymentOrTicketCommands() {
        client.post().uri("/internal/payments")
                .header(CORRELATION, "internal-trace")
                .exchange()
                .expectStatus().isNotFound();

        client.post().uri("/internal/orders/order-1/items/item-1/tickets")
                .header(CORRELATION, "internal-trace")
                .exchange()
                .expectStatus().isNotFound();

        assertThat(REQUESTS).isEmpty();
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", GatewayPurchaseContractTest::handle);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "purchase-contract-stub");
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
            String correlation = exchange.getRequestHeaders().getFirst(CORRELATION);
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            REQUESTS.add("%s %s?%s %s %s %s %s".formatted(
                    exchange.getRequestMethod(), path, query,
                    exchange.getRequestHeaders().getFirst("X-User-Id"),
                    exchange.getRequestHeaders().getFirst("X-User-Role"), correlation, body));
            byte[] response = ("{\"path\":\"" + path + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.getResponseHeaders().set(CORRELATION, correlation);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        }
    }
}
