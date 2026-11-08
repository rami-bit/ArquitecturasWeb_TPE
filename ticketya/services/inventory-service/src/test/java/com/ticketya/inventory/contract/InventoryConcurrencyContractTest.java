package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketya.inventory.InventoryServiceApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia multi-instancia: dos contextos de Inventory sobre la misma base.
 *
 * Cada escenario dispara solicitudes simultáneas alternando ambas instancias. La garantía
 * de no doble venta no depende del proceso: la dan las actualizaciones condicionales de
 * PostgreSQL y las constraints del esquema.
 */
@org.testcontainers.junit.jupiter.Testcontainers(disabledWithoutDocker = true)
class InventoryConcurrencyContractTest extends InventoryContractTestBase {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static ConfigurableApplicationContext secondInstance;
    private static int secondPort;

    @BeforeAll
    static void startSecondInventoryInstance() {
        secondInstance = new SpringApplicationBuilder(InventoryServiceApplication.class)
                .run("--server.port=0",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.sql.init.mode=always",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--inventory.catalog.base-url=" + catalogStubUrl(),
                        "--inventory.hold-ttl-seconds=600");
        secondPort = ((WebServerApplicationContext) secondInstance).getWebServer().getPort();
    }

    @AfterAll
    static void stopSecondInventoryInstance() {
        if (secondInstance != null) {
            secondInstance.close();
        }
    }

    // ------------------------------------------------------------------------ tests

    @Test
    void twoInstancesCompeteForTheSameSeatAndOnlyOneWins() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        String body = seatItem(layout.numberedSectorId(), layout.seatOneId());

        List<Supplier<HttpResponse<String>>> tasks = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            UUID orderId = UUID.randomUUID();
            int target = index % 2 == 0 ? port : secondPort;
            tasks.add(() -> sendUnchecked(target, "POST", "/internal/reservations",
                    reservationBody(orderId, eventId, body)));
        }

        List<HttpResponse<String>> responses = concurrently(tasks);

        assertThat(responses).filteredOn(r -> r.statusCode() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.statusCode() == 409).hasSize(7);
        assertThat(seatStatus(layout.seatOneId())).isEqualTo("HELD");
        assertThat(activeReservations(eventId)).isEqualTo(1);
    }

    @Test
    void twoInstancesCompeteForTheLastGeneralAdmissionPlaceWithoutOverselling() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        // El sector Campo tiene capacidad 3: se retienen 2 y queda un único lugar.
        var preReservation = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        quantityItem(layout.generalSectorId(), 2)));
        assertThat(preReservation.getStatusCode().value()).isEqualTo(201);

        List<Supplier<HttpResponse<String>>> tasks = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            UUID orderId = UUID.randomUUID();
            int target = index % 2 == 0 ? port : secondPort;
            tasks.add(() -> sendUnchecked(target, "POST", "/internal/reservations",
                    reservationBody(orderId, eventId,
                            quantityItem(layout.generalSectorId(), 1))));
        }

        List<HttpResponse<String>> responses = concurrently(tasks);

        assertThat(responses).filteredOn(r -> r.statusCode() == 201).hasSize(1);
        assertThat(responses).filteredOn(r -> r.statusCode() == 409).hasSize(5);

        int capacity = jdbc().queryForObject(
                "SELECT capacity FROM inventory_sectors WHERE id = ?",
                Integer.class, layout.generalSectorId());
        int committed = jdbc().queryForObject(
                "SELECT sold + reserved FROM inventory_sectors WHERE id = ?",
                Integer.class, layout.generalSectorId());
        assertThat(committed).isEqualTo(3).isEqualTo(capacity);

        JsonNode general = sectorNamed(
                JSON.readTree(getAvailability(eventId).getBody()).path("sectors"), "Campo");
        assertThat(general.path("available").asInt()).isZero();
    }

    @Test
    void concurrentConfirmationSellsTheReservationOnlyOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID orderId = UUID.randomUUID();
        String items = seatItem(layout.numberedSectorId(), layout.seatTwoId())
                + "," + quantityItem(layout.generalSectorId(), 1);

        var created = post("/internal/reservations",
                reservationBody(orderId, eventId, items));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        UUID reservationId = UUID.fromString(
                parseBody(created).path("reservationId").asText());

        List<Supplier<HttpResponse<String>>> confirmations = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            int target = index % 2 == 0 ? port : secondPort;
            confirmations.add(() -> sendUnchecked(target, "POST",
                    "/internal/reservations/" + reservationId + "/confirm", ""));
        }
        List<HttpResponse<String>> confirmed = concurrently(confirmations);

        assertThat(confirmed).allSatisfy(response -> {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(parse(response).path("status").asText()).isEqualTo("CONFIRMED");
        });

        assertThat(seatStatus(layout.seatTwoId())).isEqualTo("SOLD");
        assertThat(jdbc().queryForObject(
                "SELECT sold FROM inventory_sectors WHERE id = ?",
                Integer.class, layout.generalSectorId())).isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT reserved FROM inventory_sectors WHERE id = ?",
                Integer.class, layout.generalSectorId())).isZero();
        assertThat(jdbc().queryForObject(
                "SELECT COUNT(*) FROM reservation_items WHERE reservation_id = ?",
                Integer.class, reservationId)).isEqualTo(2);

        // Reintento concurrente de la misma creación: una sola reserva, respuestas idempotentes.
        String replayBody = reservationBody(orderId, eventId, items);
        List<Supplier<HttpResponse<String>>> replays = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            int target = index % 2 == 0 ? port : secondPort;
            replays.add(() -> sendUnchecked(target, "POST", "/internal/reservations", replayBody));
        }
        List<HttpResponse<String>> replayed = concurrently(replays);

        assertThat(replayed).allSatisfy(response -> {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(parse(response).path("reservationId").asText())
                    .isEqualTo(reservationId.toString());
        });
        assertThat(jdbc().queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc().queryForObject(
                "SELECT sold FROM inventory_sectors WHERE id = ?",
                Integer.class, layout.generalSectorId())).isEqualTo(1);
    }

    @Test
    void concurrentLazyExpirationReleasesTheReservationOnlyOnceAcrossInstances() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID orderId = UUID.randomUUID();
        String items = seatItem(layout.numberedSectorId(), layout.seatOneId())
                + "," + quantityItem(layout.generalSectorId(), 2);

        var created = post("/internal/reservations",
                reservationBody(orderId, eventId, items));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        UUID reservationId = UUID.fromString(
                parseBody(created).path("reservationId").asText());
        forceReservationExpiry(reservationId);

        // Ambas instancias disparan la expiración perezosa al consultar disponibilidad.
        List<Supplier<HttpResponse<String>>> tasks = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            int target = index % 2 == 0 ? port : secondPort;
            tasks.add(() -> sendUnchecked(target, "GET",
                    "/api/inventory/events/" + eventId + "/availability", ""));
        }

        List<HttpResponse<String>> responses = concurrently(tasks);
        assertThat(responses).allSatisfy(response ->
                assertThat(response.statusCode()).isEqualTo(200));

        assertThat(jdbc().queryForObject(
                "SELECT status FROM reservations WHERE id = ?",
                String.class, reservationId)).isEqualTo("EXPIRED");
        assertThat(jdbc().queryForObject(
                "SELECT expired_at IS NOT NULL FROM reservations WHERE id = ?",
                Boolean.class, reservationId)).isTrue();
        assertThat(seatStatus(layout.seatOneId())).isEqualTo("AVAILABLE");
        assertThat(jdbc().queryForObject(
                "SELECT reserved FROM inventory_sectors WHERE id = ?",
                Integer.class, layout.generalSectorId())).isZero();
        assertThat(jdbc().queryForObject(
                "SELECT sold FROM inventory_sectors WHERE id = ?",
                Integer.class, layout.generalSectorId())).isZero();
    }

    // ------------------------------------------------------------------------ utils

    private static HttpResponse<String> sendUnchecked(
            int targetPort, String method, String path, String body) {
        try {
            return send(targetPort, method, path, body);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido durante la solicitud", interrupted);
        } catch (Exception exception) {
            throw new IllegalStateException("Fallo de solicitud concurrente", exception);
        }
    }

    private static HttpResponse<String> send(int targetPort, String method, String path, String body)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + targetPort + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header(CORRELATION_HEADER, "concurrency-" + UUID.randomUUID());
        if ("POST".equals(method)) {
            request.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.GET();
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static List<HttpResponse<String>> concurrently(
            List<Supplier<HttpResponse<String>>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (Supplier<HttpResponse<String>> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.get();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private String seatStatus(UUID seatId) {
        return jdbc().queryForObject(
                "SELECT status FROM inventory_items WHERE id = ?",
                String.class, seatId);
    }

    private int activeReservations(UUID eventId) {
        return jdbc().queryForObject("""
                SELECT COUNT(*) FROM reservations r
                JOIN event_inventories i ON i.id = r.event_inventory_id
                WHERE i.event_id = ? AND r.status = 'ACTIVE'
                """, Integer.class, eventId);
    }

    private static JsonNode parse(HttpResponse<String> response) {
        try {
            return JSON.readTree(response.body());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Respuesta no JSON: " + response.statusCode() + " " + response.body(),
                    exception);
        }
    }
}
