package com.ticketya.notification.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticketya.notification.application.DeliveryWorker;
import com.ticketya.notification.application.TriggerProcessor;
import com.ticketya.notification.infrastructure.SimulatedDeliveryProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de PDF-F11 contra PostgreSQL real: ingesta idempotente, resolución
 * de destinatarios (comprador, compradores del evento, fans del artista en la
 * ciudad), entrega por email y push, reintentos ante dependencias o pasarela
 * caídas, bandeja del cliente y procesamiento concurrente sin duplicados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class NotificationServiceContractTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    private static final List<UUID> BUYERS = new CopyOnWriteArrayList<>();
    private static final List<UUID> FANS = new CopyOnWriteArrayList<>();
    private static final List<String> PREFERENCE_QUERIES = new CopyOnWriteArrayList<>();
    private static final AtomicInteger PURCHASE_FAILURES = new AtomicInteger();
    private static final HttpServer DIRECTORY = startDirectoryStub();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        String base = "http://127.0.0.1:" + DIRECTORY.getAddress().getPort();
        registry.add("notifications.purchase.url", () -> base);
        registry.add("notifications.preferences.url", () -> base);
        registry.add("notifications.scheduler.enabled", () -> "false");
        registry.add("notifications.retry.base-delay-ms", () -> "0");
        registry.add("notifications.retry.max-attempts", () -> "3");
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TriggerProcessor processor;

    @Autowired
    private DeliveryWorker worker;

    @Autowired
    private SimulatedDeliveryProvider provider;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM notification_triggers");
        BUYERS.clear();
        FANS.clear();
        PREFERENCE_QUERIES.clear();
        PURCHASE_FAILURES.set(0);
    }

    @AfterEach
    void restoreProvider() {
        provider.setFailing(false);
    }

    @AfterAll
    static void stopStub() {
        DIRECTORY.stop(0);
    }

    @Test
    void confirmedPurchaseIsIngestedOnceAndDeliveredByEmailAndPushToTheBuyer() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID order = UUID.randomUUID();
        Map<String, Object> body = order("ORDER_CONFIRMED", order, customer);

        ResponseEntity<String> first = post("/internal/notifications", body, "trace-order-1");
        ResponseEntity<String> retry = post("/internal/notifications", body, "trace-order-1");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(json.readTree(first.getBody()).path("duplicate").asBoolean()).isFalse();
        assertThat(json.readTree(retry.getBody()).path("duplicate").asBoolean()).isTrue();
        assertThat(json.readTree(retry.getBody()).path("triggerId").asText())
                .isEqualTo(json.readTree(first.getBody()).path("triggerId").asText());

        assertThat(processor.processDue()).isEqualTo(1);
        assertThat(worker.deliverDue()).isEqualTo(2);

        JsonNode inbox = inbox(customer, "CLIENT", "");
        assertThat(inbox.path("totalItems").asLong()).isEqualTo(2);
        assertThat(inbox.path("items")).extracting(item -> item.path("channel").asText())
                .containsExactlyInAnyOrder("EMAIL", "PUSH");
        JsonNode item = inbox.path("items").get(0);
        assertThat(item.path("type").asText()).isEqualTo("ORDER_CONFIRMED");
        assertThat(item.path("status").asText()).isEqualTo("SENT");
        assertThat(item.path("subject").asText()).isEqualTo("Compra confirmada: Recital de prueba");
        assertThat(item.path("body").asText()).contains(order.toString());
        assertThat(item.path("orderId").asText()).isEqualTo(order.toString());
        assertThat(item.path("correlationId").asText()).isEqualTo("trace-order-1");
        assertThat(item.path("sentAt").asText()).isNotBlank();

        assertThat(inbox(UUID.randomUUID(), "CLIENT", "").path("totalItems").asLong())
                .as("otro cliente no ve notificaciones ajenas").isZero();
    }

    @Test
    void eventCancellationNotifiesEveryBuyerOnce() throws Exception {
        UUID event = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BUYERS.addAll(List.of(first, second, first));

        Map<String, Object> change = eventChange("EVENT_CANCELLED", event, null);
        post("/internal/notifications/event-changes", change, "trace-cancel");
        ResponseEntity<String> retry = post("/internal/notifications/event-changes", change, "trace-cancel");
        assertThat(json.readTree(retry.getBody()).path("duplicate").asBoolean()).isTrue();

        processor.processDue();
        worker.deliverDue();

        for (UUID buyer : List.of(first, second)) {
            JsonNode inbox = inbox(buyer, "CLIENT", "?type=EVENT_CANCELLED");
            assertThat(inbox.path("totalItems").asLong()).isEqualTo(2);
            assertThat(inbox.path("items").get(0).path("subject").asText())
                    .isEqualTo("Evento cancelado: Recital de prueba");
            assertThat(inbox.path("items").get(0).path("eventId").asText()).isEqualTo(event.toString());
        }
        assertThat(jdbc.queryForObject("SELECT recipients FROM notification_triggers", Integer.class)).isEqualTo(2);
    }

    @Test
    void suspensionAndResumptionAreRepeatableChangesDeduplicatedByChangeId() throws Exception {
        UUID event = UUID.randomUUID();
        UUID change = UUID.randomUUID();
        BUYERS.add(UUID.randomUUID());

        assertThat(json.readTree(post("/internal/notifications/event-changes",
                eventChange("EVENT_SUSPENDED", event, change), null).getBody()).path("duplicate").asBoolean())
                .isFalse();
        assertThat(json.readTree(post("/internal/notifications/event-changes",
                eventChange("EVENT_SUSPENDED", event, change), null).getBody()).path("duplicate").asBoolean())
                .isTrue();
        assertThat(json.readTree(post("/internal/notifications/event-changes",
                eventChange("EVENT_SUSPENDED", event, UUID.randomUUID()), null).getBody())
                .path("duplicate").asBoolean()).isFalse();
        post("/internal/notifications/event-changes", eventChange("EVENT_RESUMED", event, UUID.randomUUID()), null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_triggers", Integer.class)).isEqualTo(3);
    }

    @Test
    void publishedEventNotifiesClientsWithAFavoriteArtistInTheEventCity() throws Exception {
        UUID fan = UUID.randomUUID();
        FANS.add(fan);
        UUID event = UUID.randomUUID();

        post("/internal/notifications/event-changes", eventChange("EVENT_PUBLISHED", event, null), "trace-pub");
        processor.processDue();
        worker.deliverDue();

        assertThat(PREFERENCE_QUERIES).singleElement().satisfies(query -> {
            assertThat(query).contains("city=Rosario");
            assertThat(query).contains("artist=Banda%20Uno").contains("artist=Solista%20Dos");
        });
        JsonNode inbox = inbox(fan, "CLIENT", "");
        assertThat(inbox.path("totalItems").asLong()).isEqualTo(2);
        JsonNode item = inbox.path("items").get(0);
        assertThat(item.path("type").asText()).isEqualTo("EVENT_PUBLISHED");
        assertThat(item.path("subject").asText()).isEqualTo("Nuevo evento de un artista favorito en Rosario");
        assertThat(item.path("body").asText()).contains("Banda Uno, Solista Dos");
    }

    @Test
    void unavailableDirectoryIsRetriedWithoutLosingTheNotification() throws Exception {
        UUID buyer = UUID.randomUUID();
        BUYERS.add(buyer);
        PURCHASE_FAILURES.set(1);
        post("/internal/notifications/event-changes", eventChange("EVENT_CANCELLED", UUID.randomUUID(), null), null);

        processor.processDue();
        Map<String, Object> pending = jdbc.queryForMap("SELECT status, attempts, last_error FROM notification_triggers");
        assertThat(pending.get("status")).isEqualTo("PENDING");
        assertThat(pending.get("attempts")).isEqualTo(1);
        assertThat((String) pending.get("last_error")).contains("Purchase no disponible");

        processor.processDue();
        worker.deliverDue();
        assertThat(jdbc.queryForObject("SELECT status FROM notification_triggers", String.class)).isEqualTo("PROCESSED");
        assertThat(inbox(buyer, "CLIENT", "").path("totalItems").asLong()).isEqualTo(2);
    }

    @Test
    void directoryDownBeyondTheRetryBudgetMarksTheTriggerFailed() {
        PURCHASE_FAILURES.set(10);
        post("/internal/notifications/event-changes", eventChange("EVENT_CANCELLED", UUID.randomUUID(), null), null);
        for (int i = 0; i < 3; i++) {
            processor.processDue();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM notification_triggers", String.class)).isEqualTo("FAILED");
        assertThat(processor.processDue()).isZero();
    }

    @Test
    void failingGatewayRetriesDeliveryAndFinallyMarksItFailed() throws Exception {
        UUID customer = UUID.randomUUID();
        post("/internal/notifications", order("ORDER_CONFIRMED", UUID.randomUUID(), customer), null);
        processor.processDue();
        provider.setFailing(true);

        worker.deliverDue();
        assertThat(jdbc.queryForList("SELECT status FROM notifications", String.class)).containsOnly("PENDING");
        worker.deliverDue();
        worker.deliverDue();

        JsonNode inbox = inbox(customer, "CLIENT", "");
        assertThat(inbox.path("items")).extracting(item -> item.path("status").asText()).containsOnly("FAILED");
        assertThat(inbox.path("items").get(0).path("attempts").asInt()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT last_error FROM notifications LIMIT 1", String.class))
                .contains("no disponible");
    }

    @Test
    void recoveredGatewayDeliversPendingNotifications() throws Exception {
        UUID customer = UUID.randomUUID();
        post("/internal/notifications", order("ORDER_CANCELLED", UUID.randomUUID(), customer), null);
        processor.processDue();
        provider.setFailing(true);
        worker.deliverDue();
        provider.setFailing(false);
        worker.deliverDue();

        JsonNode inbox = inbox(customer, "CLIENT", "");
        assertThat(inbox.path("items")).extracting(item -> item.path("status").asText()).containsOnly("SENT");
        assertThat(inbox.path("items").get(0).path("subject").asText()).isEqualTo("Tu compra no pudo completarse");
    }

    @Test
    void concurrentProcessorsNeverDuplicateNotifications() throws Exception {
        int triggers = 20;
        for (int i = 0; i < triggers; i++) {
            post("/internal/notifications", order("ORDER_CONFIRMED", UUID.randomUUID(), UUID.randomUUID()), null);
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return processor.processDue();
                }));
            }
            start.countDown();
            int claimed = 0;
            for (Future<Integer> result : results) {
                claimed += result.get(30, TimeUnit.SECONDS);
            }
            assertThat(claimed).as("cada disparador lo reclama una sola instancia").isEqualTo(triggers);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class)).isEqualTo(triggers * 2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_triggers WHERE status = 'PROCESSED'",
                Integer.class)).isEqualTo(triggers);
    }

    @Test
    void invalidFactsAndUnauthorizedInboxRequestsAreRejected() {
        Map<String, Object> badType = order("EVENT_CANCELLED", UUID.randomUUID(), UUID.randomUUID());
        assertThat(post("/internal/notifications", badType, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> missingOrder = order("ORDER_CONFIRMED", UUID.randomUUID(), UUID.randomUUID());
        missingOrder.remove("orderId");
        assertThat(post("/internal/notifications", missingOrder, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> badChange = eventChange("ORDER_CONFIRMED", UUID.randomUUID(), null);
        ResponseEntity<String> rejected = post("/internal/notifications/event-changes", badChange, null);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        for (String role : new String[]{"ADMIN", "ORGANIZER", "ACCESS_STAFF", null}) {
            assertThat(inboxResponse(UUID.randomUUID().toString(), role, "").getStatusCode())
                    .as("rol %s", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(inboxResponse("not-a-uuid", "CLIENT", "").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(inboxResponse(UUID.randomUUID().toString(), "CLIENT", "?size=0").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_triggers", Integer.class)).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> order(String type, UUID order, UUID customer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("orderId", order.toString());
        body.put("customerId", customer.toString());
        body.put("eventId", UUID.randomUUID().toString());
        body.put("eventName", "Recital de prueba");
        body.put("eventStartsAt", "2027-03-10T21:00:00Z");
        return body;
    }

    private Map<String, Object> eventChange(String type, UUID event, UUID change) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("eventId", event.toString());
        if (change != null) {
            body.put("changeId", change.toString());
        }
        body.put("eventName", "Recital de prueba");
        body.put("startsAt", "2027-03-10T21:00:00Z");
        body.put("city", "Rosario");
        body.put("participants", List.of("Banda Uno", "Solista Dos"));
        return body;
    }

    private ResponseEntity<String> post(String path, Map<String, Object> body, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.set("X-Correlation-Id", correlationId);
        }
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode inbox(UUID customer, String role, String query) throws Exception {
        ResponseEntity<String> response = inboxResponse(customer.toString(), role, query);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(response.getBody());
    }

    private ResponseEntity<String> inboxResponse(String user, String role, String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", user);
        if (role != null) {
            headers.set("X-User-Role", role);
        }
        return http.exchange("/api/notifications/me" + query, HttpMethod.GET, new HttpEntity<>(headers),
                String.class);
    }

    private static HttpServer startDirectoryStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", NotificationServiceContractTest::respond);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "notification-directory-stub");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            int status = 200;
            List<UUID> ids;
            if (path.endsWith("/buyers")) {
                if (PURCHASE_FAILURES.getAndDecrement() > 0) {
                    status = 503;
                }
                ids = BUYERS;
            } else if (path.equals("/internal/preferences/matches")) {
                PREFERENCE_QUERIES.add(exchange.getRequestURI().getRawQuery());
                ids = FANS;
            } else {
                status = 404;
                ids = List.of();
            }
            StringBuilder body = new StringBuilder("{\"customerIds\":[");
            for (int i = 0; i < ids.size(); i++) {
                body.append(i == 0 ? "" : ",").append('"').append(ids.get(i)).append('"');
            }
            byte[] bytes = body.append("]}").toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
