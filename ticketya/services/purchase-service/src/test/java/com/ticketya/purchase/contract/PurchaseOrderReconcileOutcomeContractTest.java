package com.ticketya.purchase.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 3.3 (3): reconciliar una orden PAYMENT_PENDING cuyo pago del stub
 * evoluciona después: se aprueba, se rechaza o sigue pendiente. En todos los
 * casos no se vuelve a cobrar y los efectos (confirmación, liberación, límite)
 * coinciden con el desenlace.
 */
@Testcontainers(disabledWithoutDocker = true)
class PurchaseOrderReconcileOutcomeContractTest extends PurchaseContractTestBase {

    private final UUID buyer = UUID.randomUUID();

    private record PendingOrder(String orderId, UUID eventId) {
    }

    private PendingOrder createPendingOrder(String idempotencyKey) {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        ResponseEntity<String> created = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                        "WALLET", "tok_pending"),
                buyerHeaders(buyer, idempotencyKey));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode order = parseBody(created);
        assertThat(order.path("status").asText()).isEqualTo("PAYMENT_PENDING");
        return new PendingOrder(order.path("orderId").asText(), eventId);
    }

    private ResponseEntity<String> reconcile(String orderId) {
        return post("/api/orders/" + orderId + "/reconcile", null,
                Map.of(USER_HEADER, buyer.toString()));
    }

    @Test
    void reconcileApprovedPaymentConfirmsTheOrderAndCommitsTheLimit() {
        PendingOrder pending = createPendingOrder("key-reconcile-approved-1");

        // El pago evoluciona a APPROVED en la pasarela simulada.
        forcePaymentStatus(UUID.fromString(pending.orderId()), "APPROVED");

        ResponseEntity<String> reconciled = reconcile(pending.orderId());

        assertThat(reconciled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = parseBody(reconciled);
        assertThat(order.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(order.path("payment").path("status").asText()).isEqualTo("APPROVED");
        assertThat(order.path("items").get(0).path("tickets")).hasSize(1);

        // No se vuelve a cobrar ni re-reservar; la reserva se confirmó y se emitieron tickets.
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments")).isEqualTo(1);
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release")).isZero();
        assertThat(countTicketIssuances()).isEqualTo(1);

        // El límite quedó comprometido: quedan 5 unidades dentro del límite de 6.
        ResponseEntity<String> withinLimit = createOrder(
                orderBody(pending.eventId(),
                        quantityItem(layoutFor(pending.eventId()).campoSectorId(), 5),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-reconcile-approved-2"));
        assertThat(withinLimit.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> overLimit = createOrder(
                orderBody(pending.eventId(),
                        quantityItem(layoutFor(pending.eventId()).campoSectorId(), 1),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-reconcile-approved-3"));
        assertProblem(overLimit, HttpStatus.CONFLICT);
    }

    @Test
    void reconcileRejectedPaymentRejectsTheOrderAndReleasesReservationAndLimit() {
        PendingOrder pending = createPendingOrder("key-reconcile-rejected-1");

        // El pago evoluciona a REJECTED en la pasarela simulada.
        forcePaymentStatus(UUID.fromString(pending.orderId()), "REJECTED");

        ResponseEntity<String> reconciled = reconcile(pending.orderId());

        assertThat(reconciled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = parseBody(reconciled);
        assertThat(order.path("status").asText()).isEqualTo("REJECTED");
        assertThat(order.path("payment").path("status").asText()).isEqualTo("REJECTED");
        for (JsonNode item : order.path("items")) {
            assertThat(item.path("tickets")).isEmpty();
        }

        // No se vuelve a cobrar; la reserva se liberó y no se emitieron tickets.
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments")).isEqualTo(1);
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm")).isZero();
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release")).isEqualTo(1);
        assertThat(countTicketIssuances()).isZero();

        // El límite quedó liberado: la misma cantidad vuelve a entrar.
        Layout layout = layoutFor(pending.eventId());
        ResponseEntity<String> retry = createOrder(
                orderBody(pending.eventId(), quantityItem(layout.campoSectorId(), 6),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-reconcile-rejected-2"));
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(parseBody(retry).path("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void reconcileStillPendingPaymentKeepsTheOrderReconcilableWithoutSideEffects() {
        PendingOrder pending = createPendingOrder("key-reconcile-still-pending-1");

        ResponseEntity<String> reconciled = reconcile(pending.orderId());

        assertThat(reconciled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = parseBody(reconciled);
        assertThat(order.path("status").asText()).isEqualTo("PAYMENT_PENDING");
        assertThat(order.path("payment").path("status").asText()).isEqualTo("PENDING");
        for (JsonNode item : order.path("items")) {
            assertThat(item.path("tickets")).isEmpty();
        }

        // Sin confirmaciones, liberaciones ni emisiones; y sin un segundo cobro.
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm")).isZero();
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release")).isZero();
        assertThat(countTicketIssuances()).isZero();
    }
}
