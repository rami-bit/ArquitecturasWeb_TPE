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
 * Contrato de lectura y reconciliación: `GET /api/orders/{orderId}` propio y
 * ajeno, `GET /api/orders` paginado y filtrado por `status`, y
 * `POST /api/orders/{orderId}/reconcile` para órdenes pendientes.
 */
@Testcontainers(disabledWithoutDocker = true)
class PurchaseOrderQueryContractTest extends PurchaseContractTestBase {

    private static final org.assertj.core.data.Offset<Double> MONEY =
            org.assertj.core.data.Offset.offset(0.001);

    private final UUID buyer = UUID.randomUUID();
    private final UUID otherBuyer = UUID.randomUUID();

    @Test
    void getOwnOrderReturns200AndForeignOrUnknownOrderReturns404() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");

        ResponseEntity<String> created = createOrder(body, buyerHeaders(buyer, "key-get-1"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String orderId = parseBody(created).path("orderId").asText();

        // Orden propia.
        ResponseEntity<String> own = get("/api/orders/" + orderId,
                Map.of(USER_HEADER, buyer.toString()));
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = parseBody(own);
        assertThat(order.path("orderId").asText()).isEqualTo(orderId);
        assertThat(order.path("customerId").asText()).isEqualTo(buyer.toString());
        assertThat(order.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(order.path("payment").path("status").asText()).isEqualTo("APPROVED");
        assertThat(order.path("items")).isNotEmpty();

        // Orden de otro comprador: 404 sin revelar su existencia.
        ResponseEntity<String> foreign = get("/api/orders/" + orderId,
                Map.of(USER_HEADER, otherBuyer.toString()));
        assertProblem(foreign, HttpStatus.NOT_FOUND);

        // Orden inexistente.
        ResponseEntity<String> unknown = get("/api/orders/" + UUID.randomUUID(),
                Map.of(USER_HEADER, buyer.toString()));
        assertProblem(unknown, HttpStatus.NOT_FOUND);
    }

    @Test
    void listOrdersIsPaginatedAndFilteredByStatus() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);

        ResponseEntity<String> confirmed = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-list-1"));
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String confirmedId = parseBody(confirmed).path("orderId").asText();

        ResponseEntity<String> rejected = createOrder(
                orderBody(eventId, seatItem(layout.plateaSectorId(), layout.seatOneId()),
                        "CARD", "tok_rejected"),
                buyerHeaders(buyer, "key-list-2"));
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        String rejectedId = parseBody(rejected).path("orderId").asText();

        // Listado paginado de las órdenes propias.
        ResponseEntity<String> page = get("/api/orders?page=0&size=10",
                Map.of(USER_HEADER, buyer.toString()));
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = parseBody(page);
        assertThat(body.path("items").isArray()).isTrue();
        assertThat(body.path("page").asInt()).isEqualTo(0);
        assertThat(body.path("size").asInt()).isEqualTo(10);
        assertThat(body.path("totalElements").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(body.path("totalPages").asInt()).isGreaterThanOrEqualTo(1);
        for (JsonNode item : body.path("items")) {
            assertThat(item.path("customerId").asText()).isEqualTo(buyer.toString());
        }

        // Filtro por estado.
        ResponseEntity<String> rejectedPage = get("/api/orders?status=REJECTED",
                Map.of(USER_HEADER, buyer.toString()));
        assertThat(rejectedPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode rejectedBody = parseBody(rejectedPage);
        assertThat(rejectedBody.path("items")).isNotEmpty();
        boolean foundRejected = false;
        for (JsonNode item : rejectedBody.path("items")) {
            assertThat(item.path("status").asText()).isEqualTo("REJECTED");
            foundRejected |= rejectedId.equals(item.path("orderId").asText());
        }
        assertThat(foundRejected).as("la orden rechazada aparece filtrada").isTrue();

        ResponseEntity<String> confirmedPage = get("/api/orders?status=CONFIRMED",
                Map.of(USER_HEADER, buyer.toString()));
        assertThat(confirmedPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        boolean foundConfirmed = false;
        for (JsonNode item : parseBody(confirmedPage).path("items")) {
            assertThat(item.path("status").asText()).isEqualTo("CONFIRMED");
            foundConfirmed |= confirmedId.equals(item.path("orderId").asText());
        }
        assertThat(foundConfirmed).as("la orden confirmada aparece filtrada").isTrue();

        // Un cliente sólo ve sus propias órdenes.
        ResponseEntity<String> otherPage = get("/api/orders",
                Map.of(USER_HEADER, otherBuyer.toString()));
        assertThat(otherPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode otherBody = parseBody(otherPage);
        assertThat(otherBody.path("totalElements").asInt()).isZero();
        assertThat(otherBody.path("items")).isEmpty();

        // Parámetros inválidos.
        assertProblem(get("/api/orders?size=51", Map.of(USER_HEADER, buyer.toString())),
                HttpStatus.BAD_REQUEST);
        assertProblem(get("/api/orders?page=-1", Map.of(USER_HEADER, buyer.toString())),
                HttpStatus.BAD_REQUEST);
        assertProblem(get("/api/orders?status=BOGUS", Map.of(USER_HEADER, buyer.toString())),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void reconcilePendingOrderReturns200AndTerminalOrderReturns409() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);

        ResponseEntity<String> pending = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                        "WALLET", "tok_pending"),
                buyerHeaders(buyer, "key-reconcile-1"));
        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String pendingId = parseBody(pending).path("orderId").asText();

        // Reconciliar una orden con pago pendiente: 200 y sigue pendiente.
        ResponseEntity<String> reconciled = post("/api/orders/" + pendingId + "/reconcile",
                null, Map.of(USER_HEADER, buyer.toString()));
        assertThat(reconciled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = parseBody(reconciled);
        assertThat(order.path("orderId").asText()).isEqualTo(pendingId);
        assertThat(order.path("status").asText()).isEqualTo("PAYMENT_PENDING");

        // Una orden terminal no es reconciliable.
        ResponseEntity<String> confirmed = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-reconcile-2"));
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String confirmedId = parseBody(confirmed).path("orderId").asText();
        assertProblem(post("/api/orders/" + confirmedId + "/reconcile", null,
                        Map.of(USER_HEADER, buyer.toString())),
                HttpStatus.CONFLICT);

        // Orden inexistente.
        assertProblem(post("/api/orders/" + UUID.randomUUID() + "/reconcile", null,
                        Map.of(USER_HEADER, buyer.toString())),
                HttpStatus.NOT_FOUND);

        // Identidad obligatoria también en la reconciliación.
        assertProblem(post("/api/orders/" + pendingId + "/reconcile", null,
                        Map.of(IDEMPOTENCY_HEADER, "unused")),
                HttpStatus.FORBIDDEN);
    }
}
