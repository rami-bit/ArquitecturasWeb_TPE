package com.ticketya.purchase.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 3.3 (5): compras distintas y concurrentes del mismo cliente/evento que
 * intentan exceder el límite de 6. La validación atómica del límite debe
 * aceptar sólo las unidades que caben y rechazar el resto con 409, sin
 * contabilizar de más.
 */
@Testcontainers(disabledWithoutDocker = true)
class PurchaseOrderConcurrentLimitContractTest extends PurchaseContractTestBase {

    private final UUID buyer = UUID.randomUUID();

    @Test
    void concurrentPurchasesForTheSameClientAndEventDoNotExceedTheLimit()
            throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);

        // Base confirmada de 4 unidades: quedan 2 dentro del límite de 6.
        ResponseEntity<String> base = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 4),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-conc-base"));
        assertThat(base.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Cuatro compras concurrentes de 2 unidades cada una: sólo una cabe.
        int contenders = 4;
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < contenders; attempt++) {
                final String key = "key-conc-" + attempt;
                futures.add(executor.submit(() -> {
                    start.await();
                    return createOrder(
                            orderBody(eventId, quantityItem(layout.campoSectorId(), 2),
                                    "CARD", "tok_approved"),
                            buyerHeaders(buyer, key));
                }));
            }
            start.countDown();

            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(90, TimeUnit.SECONDS));
            }

            long created = responses.stream()
                    .filter(response -> response.getStatusCode() == HttpStatus.CREATED)
                    .count();
            long conflicts = responses.stream()
                    .filter(response -> response.getStatusCode() == HttpStatus.CONFLICT)
                    .count();
            assertThat(created)
                    .as("sólo la compra cuyas unidades caben en el límite se confirma")
                    .isEqualTo(1);
            assertThat(conflicts).isEqualTo(contenders - 1);
            for (ResponseEntity<String> response : responses) {
                if (response.getStatusCode() == HttpStatus.CONFLICT) {
                    assertProblem(response, HttpStatus.CONFLICT);
                }
            }
        } finally {
            executor.shutdownNow();
        }

        // Verificación de la suma real de unidades por listado de órdenes propias.
        ResponseEntity<String> list = get("/api/orders",
                Map.of(USER_HEADER, buyer.toString()));
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        int totalUnits = 0;
        for (JsonNode order : parseBody(list).path("items")) {
            if (eventId.toString().equals(order.path("eventId").asText())) {
                for (JsonNode item : order.path("items")) {
                    totalUnits += item.path("quantity").asInt();
                }
            }
        }
        assertThat(totalUnits)
                .as("las compras concurrentes no superan el límite de 6")
                .isEqualTo(6);

        // Con el límite agotado, una unidad más es rechazada.
        ResponseEntity<String> extra = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-conc-extra"));
        assertProblem(extra, HttpStatus.CONFLICT);
    }

    @Test
    void pendingOrdersAlsoCountTowardTheLimitForConcurrentPurchases() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);

        // Una orden pendiente de pago contabiliza sus unidades.
        ResponseEntity<String> pending = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 6),
                        "WALLET", "tok_pending"),
                buyerHeaders(buyer, "key-conc-pending-1"));
        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(parseBody(pending).path("status").asText())
                .isEqualTo("PAYMENT_PENDING");

        // Otra compra concurrente del mismo cliente/evento no puede exceder el límite.
        ResponseEntity<String> rejected = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-conc-pending-2"));
        assertProblem(rejected, HttpStatus.CONFLICT);

        // Sin reserva ni cobro en la compra rechazada.
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments")).isEqualTo(1);
    }
}
