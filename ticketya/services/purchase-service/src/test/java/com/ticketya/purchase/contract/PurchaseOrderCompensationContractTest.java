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
 * Tarea 3.3 (1): pago aprobado + Inventory 409 al confirmar la reserva.
 * La orden queda COMPENSATED, se solicita el reintegro a Payment, se libera la
 * reserva en Inventory, no se emiten tickets y el límite por cliente/evento
 * queda liberado.
 */
@Testcontainers(disabledWithoutDocker = true)
class PurchaseOrderCompensationContractTest extends PurchaseContractTestBase {

    private final UUID buyer = UUID.randomUUID();

    @Test
    void approvedPaymentWithUnconfirmableReservationCompensatesTheOrder() {
        UUID eventId = CONFIRM_CONFLICT_EVENT;
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 6),
                "CARD", "tok_approved");

        ResponseEntity<String> response = createOrder(body,
                buyerHeaders(buyer, "key-compensate-1"));

        assertProblem(response, HttpStatus.CONFLICT);
        JsonNode problem = parseBody(response);
        assertThat(problem.path("orderStatus").asText()).isEqualTo("COMPENSATED");
        assertThat(problem.path("orderId").asText(null)).isNotBlank();

        // La saga reservó y cobró, pero al confirmar compensó con reintegro y liberación.
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release"))
                .as("la reserva se libera al compensar")
                .isEqualTo(1);
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments")).isEqualTo(1);
        assertThat(countPostsWithSuffix(PAYMENT_REQUESTS, "/refund"))
                .as("se solicita el reintegro del pago aprobado")
                .isEqualTo(1);
        assertThat(countTicketIssuances())
                .as("una orden compensada no emite tickets")
                .isZero();

        // La orden persistida queda compensada, con pago reintegrado y sin tickets expuestos.
        String orderId = problem.path("orderId").asText();
        ResponseEntity<String> fetched = get("/api/orders/" + orderId,
                Map.of(USER_HEADER, buyer.toString()));
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = parseBody(fetched);
        assertThat(order.path("status").asText()).isEqualTo("COMPENSATED");
        assertThat(order.path("payment").path("status").asText()).isEqualTo("REFUNDED");
        for (JsonNode item : order.path("items")) {
            assertThat(item.path("tickets"))
                    .as("la compensación no expone tickets")
                    .isEmpty();
        }

        // El límite quedó liberado: una compra de 1 unidad pasa la validación
        // atómica del límite (6 pendientes + 1 excederían si no se liberaran) y
        // entra a la saga. El evento reservado vuelve a compensar, por lo que el
        // 409 del reintento lleva orderStatus COMPENSATED; un 409 por límite
        // excedido no incluiría ese campo.
        ResponseEntity<String> retry = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-compensate-2"));
        assertProblem(retry, HttpStatus.CONFLICT);
        assertThat(parseBody(retry).path("orderStatus").asText())
                .as("el reintento superó el límite liberado: el 409 es de la "
                        + "compensación del evento reservado, no del límite")
                .isEqualTo("COMPENSATED");
    }

    @Test
    void compensatingTwiceDoesNotDuplicateRefundOrRelease() {
        UUID eventId = CONFIRM_CONFLICT_EVENT;
        Layout layout = layoutFor(eventId);

        ResponseEntity<String> first = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-compensate-replay-1"));
        assertProblem(first, HttpStatus.CONFLICT);

        long refunds = countPostsWithSuffix(PAYMENT_REQUESTS, "/refund");
        long releases = countPostsWithSuffix(INVENTORY_REQUESTS, "/release");

        // Reintento con la misma clave y el mismo contenido: idempotente.
        ResponseEntity<String> replay = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-compensate-replay-1"));

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = parseBody(replay);
        assertThat(order.path("status").asText()).isEqualTo("COMPENSATED");

        assertThat(countPostsWithSuffix(PAYMENT_REQUESTS, "/refund"))
                .as("el reintegro no se duplica al reintentar")
                .isEqualTo(refunds);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release"))
                .as("la liberación no se duplica al reintentar")
                .isEqualTo(releases);
    }
}
