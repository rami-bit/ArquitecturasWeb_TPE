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
 * Contrato de `POST /api/orders`: saga completa aprobada, replay idempotente,
 * conflicto de idempotencia, pago rechazado (402), pago incierto (202),
 * falta de disponibilidad (409 sin cobrar) y límite de unidades por cliente
 * y evento.
 */
@Testcontainers(disabledWithoutDocker = true)
class PurchaseOrderCreationContractTest extends PurchaseContractTestBase {

    private static final org.assertj.core.data.Offset<Double> MONEY =
            org.assertj.core.data.Offset.offset(0.001);

    private final UUID buyer = UUID.randomUUID();

    @Test
    void approvedOrderReturns201ConfirmedWithItemsPaymentAndTickets() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String items = seatItem(layout.plateaSectorId(), layout.seatOneId(), layout.seatTwoId())
                + "," + quantityItem(layout.campoSectorId(), 2);
        String body = orderBody(eventId, items, "CARD", "tok_approved");

        ResponseEntity<String> response = createOrder(body,
                buyerHeaders(buyer, "key-approved-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getFirst(CORRELATION_HEADER)).isNotBlank();
        JsonNode order = parseBody(response);
        UUID orderId = UUID.fromString(order.path("orderId").asText());
        assertThat(order.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(order.path("customerId").asText()).isEqualTo(buyer.toString());
        assertThat(order.path("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(order.path("currency").asText()).isEqualTo("ARS");
        assertThat(order.path("totalAmount").asDouble()).isCloseTo(40000.00, MONEY);
        assertThat(order.path("reservationExpiresAt").isNull())
                .as("una orden CONFIRMED ya no depende de una reserva vigente")
                .isTrue();
        assertThat(order.path("createdAt").asText(null)).isNotBlank();
        assertThat(order.path("updatedAt").asText(null)).isNotBlank();

        JsonNode payment = order.path("payment");
        assertThat(payment.path("paymentId").asText(null)).isNotBlank();
        assertThat(payment.path("status").asText()).isEqualTo("APPROVED");
        assertThat(payment.path("amount").asDouble()).isCloseTo(40000.00, MONEY);
        assertThat(payment.path("currency").asText()).isEqualTo("ARS");
        assertThat(payment.path("method").asText()).isEqualTo("CARD");

        JsonNode orderItems = order.path("items");
        assertThat(orderItems).hasSize(2);
        JsonNode numbered = itemByType(orderItems, "NUMBERED");
        assertThat(numbered.path("quantity").asInt()).isEqualTo(2);
        assertThat(numbered.path("unitPrice").asDouble()).isCloseTo(15000.00, MONEY);
        assertThat(numbered.path("subtotal").asDouble()).isCloseTo(30000.00, MONEY);
        assertThat(numbered.path("units")).hasSize(2);
        assertThat(numbered.path("tickets")).hasSize(2);
        JsonNode general = itemByType(orderItems, "GENERAL_ADMISSION");
        assertThat(general.path("quantity").asInt()).isEqualTo(2);
        assertThat(general.path("unitPrice").asDouble()).isCloseTo(5000.00, MONEY);
        assertThat(general.path("subtotal").asDouble()).isCloseTo(10000.00, MONEY);
        assertThat(general.path("units")).hasSize(2);
        assertThat(general.path("tickets")).hasSize(2);

        JsonNode ticket = numbered.path("tickets").get(0);
        assertThat(ticket.path("ticketId").asText(null)).isNotBlank();
        assertThat(ticket.path("unitNumber").asInt()).isEqualTo(1);
        assertThat(ticket.path("status").asText()).isEqualTo("ISSUED");
        assertThat(ticket.path("qrPayload").asText(null)).isNotBlank();
        assertThat(ticket.path("qrPngBase64").asText(null)).isNotBlank();

        // La saga reservó, confirmó, cobró y emitió un ticket por ítem.
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release")).isZero();
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments")).isEqualTo(1);
        assertThat(countTicketIssuances()).isEqualTo(2);
        String ordersPrefix = "/internal/orders/" + orderId + "/items/";
        for (RecordedRequest request : TICKET_REQUESTS) {
            assertThat(request.path())
                    .as("emisión por ítem de la orden creada")
                    .startsWith(ordersPrefix)
                    .endsWith("/tickets");
        }
    }

    @Test
    void identicalReplayReturnsTheSameOrderWithoutNewStubCalls() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String items = quantityItem(layout.campoSectorId(), 2);
        String body = orderBody(eventId, items, "CARD", "tok_approved");

        ResponseEntity<String> created = createOrder(body, buyerHeaders(buyer, "key-replay-1"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderId = parseBody(created).path("orderId").asText();

        long catalogCalls = CATALOG_REQUESTS.size();
        long inventoryCreates = countPosts(INVENTORY_REQUESTS, "/internal/reservations");
        long inventoryConfirms = countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm");
        long paymentCreates = countPosts(PAYMENT_REQUESTS, "/internal/payments");
        long ticketCalls = countTicketIssuances();

        ResponseEntity<String> replay = createOrder(body, buyerHeaders(buyer, "key-replay-1"));

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode second = parseBody(replay);
        assertThat(second.path("orderId").asText()).isEqualTo(orderId);
        assertThat(second.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(second.path("totalAmount").asDouble()).isCloseTo(10000.00, MONEY);

        // Sin nuevas llamadas a ningún stub.
        assertThat(CATALOG_REQUESTS).hasSize((int) catalogCalls);
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations"))
                .isEqualTo(inventoryCreates);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm"))
                .isEqualTo(inventoryConfirms);
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments"))
                .isEqualTo(paymentCreates);
        assertThat(countTicketIssuances()).isEqualTo(ticketCalls);
    }

    @Test
    void sameIdempotencyKeyWithDifferentBodyReturnsConflict() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String original = orderBody(eventId, quantityItem(layout.campoSectorId(), 2),
                "CARD", "tok_approved");

        ResponseEntity<String> created = createOrder(original,
                buyerHeaders(buyer, "key-conflict-1"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderId = parseBody(created).path("orderId").asText();

        String different = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");
        ResponseEntity<String> conflict = createOrder(different,
                buyerHeaders(buyer, "key-conflict-1"));

        assertProblem(conflict, HttpStatus.CONFLICT);

        // La orden original queda intacta y no hubo un segundo cobro.
        ResponseEntity<String> fetched = get("/api/orders/" + orderId,
                Map.of(USER_HEADER, buyer.toString()));
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = parseBody(fetched);
        assertThat(order.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(order.path("totalAmount").asDouble()).isCloseTo(10000.00, MONEY);
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments")).isEqualTo(1);
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
    }

    @Test
    void rejectedPaymentReturns402ReleasesReservationAndIssuesNoTickets() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, seatItem(layout.plateaSectorId(), layout.seatOneId()),
                "CARD", "tok_rejected");

        ResponseEntity<String> response = createOrder(body,
                buyerHeaders(buyer, "key-rejected-1"));

        assertProblem(response, HttpStatus.PAYMENT_REQUIRED);
        JsonNode problem = parseBody(response);
        assertThat(problem.path("orderStatus").asText()).isEqualTo("REJECTED");
        String orderId = problem.path("orderId").asText();
        assertThat(UUID.fromString(orderId)).isNotNull();
        assertThat(problem.path("correlationId").asText(null)).isNotBlank();

        // El stub de Inventory recibió la reserva y luego la liberación; sin confirmación.
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm")).isZero();
        assertThat(countTicketIssuances()).isZero();
        assertThat(PAYMENT_REQUESTS)
                .anyMatch(request -> request.body() != null
                        && request.body().contains("tok_rejected"));

        // La orden queda REJECTED y sin tickets.
        ResponseEntity<String> fetched = get("/api/orders/" + orderId,
                Map.of(USER_HEADER, buyer.toString()));
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = parseBody(fetched);
        assertThat(order.path("status").asText()).isEqualTo("REJECTED");
        assertThat(order.path("reservationExpiresAt").isNull()).isTrue();
        for (JsonNode item : order.path("items")) {
            assertThat(item.path("tickets"))
                    .as("una orden rechazada no expone tickets")
                    .isEmpty();
        }
    }

    @Test
    void pendingPaymentReturns202AndKeepsReservation() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 2),
                "WALLET", "tok_pending");

        ResponseEntity<String> response = createOrder(body,
                buyerHeaders(buyer, "key-pending-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode order = parseBody(response);
        assertThat(order.path("status").asText()).isEqualTo("PAYMENT_PENDING");
        assertThat(order.path("payment").path("status").asText()).isEqualTo("PENDING");
        assertThat(order.path("reservationExpiresAt").asText(null))
                .as("la reserva se conserva para reconciliar después")
                .isNotBlank();

        // La reserva no se libera ni se confirma; aún no hay tickets.
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release")).isZero();
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm")).isZero();
        assertThat(countTicketIssuances()).isZero();
    }

    @Test
    void unavailableInventoryReturns409WithoutCallingPayment() {
        UUID eventId = NO_AVAILABILITY_EVENT;
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");

        ResponseEntity<String> response = createOrder(body,
                buyerHeaders(buyer, "key-unavailable-1"));

        assertProblem(response, HttpStatus.CONFLICT);
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(PAYMENT_REQUESTS)
                .as("sin disponibilidad no se invoca Payment")
                .isEmpty();
        assertThat(TICKET_REQUESTS)
                .as("sin disponibilidad no se emiten tickets")
                .isEmpty();
    }

    @Test
    void exceedingSixUnitLimitReturns409WithoutReservingOrCharging() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);

        ResponseEntity<String> first = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 6), "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-limit-1"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        long inventoryCreates = countPosts(INVENTORY_REQUESTS, "/internal/reservations");
        long paymentCreates = countPosts(PAYMENT_REQUESTS, "/internal/payments");
        long ticketCalls = countTicketIssuances();

        ResponseEntity<String> second = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 1), "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-limit-2"));

        assertProblem(second, HttpStatus.CONFLICT);
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations"))
                .isEqualTo(inventoryCreates);
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments"))
                .isEqualTo(paymentCreates);
        assertThat(countTicketIssuances()).isEqualTo(ticketCalls);
    }

    @Test
    void rejectedPaymentReleasesTheUnitLimitForNewOrders() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);

        ResponseEntity<String> rejected = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 6), "CARD", "tok_rejected"),
                buyerHeaders(buyer, "key-limit-release-1"));
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);

        // El límite quedó liberado tras el rechazo: la misma cantidad vuelve a entrar.
        ResponseEntity<String> approved = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 6), "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-limit-release-2"));
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(parseBody(approved).path("status").asText()).isEqualTo("CONFIRMED");
    }

    // ------------------------------------------------------------------ helpers

    private static JsonNode itemByType(JsonNode items, String sectorType) {
        for (JsonNode item : items) {
            if (sectorType.equals(item.path("sectorType").asText(null))) {
                return item;
            }
        }
        throw new AssertionError("no hay ítem de tipo " + sectorType);
    }
}
