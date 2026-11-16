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
 * Requisito "Manejar un pago incierto y reconciliar la orden": Payment procesa
 * y persiste el cobro APPROVED pero la conexión se corta antes de devolver la
 * respuesta HTTP. Purchase no debe asumir rechazo ni compensar: responde 202
 * con la orden PAYMENT_PENDING, conserva la reserva y el límite, y la
 * reconciliación posterior consulta el pago por orden (by-order), lo encuentra
 * APPROVED, confirma el inventario, emite tickets y expone paymentId + estado
 * APPROVED sin repetir cobro, reserva, release ni refund.
 */
@Testcontainers(disabledWithoutDocker = true)
class PurchaseOrderPaymentTimeoutContractTest extends PurchaseContractTestBase {

    private final UUID buyer = UUID.randomUUID();

    @Test
    void droppedPaymentResponseKeepsOrderPendingAndReconcileConfirmsTheApprovedCharge() {
        UUID eventId = PAYMENT_DROPPED_RESPONSE_EVENT;
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");
        Map<String, String> headers = buyerHeaders(buyer, "key-payment-dropped-1");

        dropPaymentChargeResponse();
        ResponseEntity<String> created = createOrder(body, headers);

        // El cobro quedó APPROVED persistido en Payment, pero Purchase no recibió
        // la respuesta: no asume rechazo, no libera la reserva y deja la orden
        // en PAYMENT_PENDING (202) reconciliable.
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode order = parseBody(created);
        assertThat(order.path("status").asText()).isEqualTo("PAYMENT_PENDING");
        assertThat(order.path("reservationExpiresAt").asText(null))
                .as("la reserva se conserva con vencimiento")
                .isNotBlank();
        for (JsonNode item : order.path("items")) {
            assertThat(item.path("tickets")).isEmpty();
        }

        // Exactamente 1 intento de cobro y 1 reserva; sin confirm, sin release,
        // sin refund y sin emisión de tickets.
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments")).isEqualTo(1);
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm")).isZero();
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release")).isZero();
        assertThat(countPostsWithSuffix(PAYMENT_REQUESTS, "/refund")).isZero();
        assertThat(countTicketIssuances()).isZero();

        // Reconciliación: GET by-order informa APPROVED y la orden se confirma.
        String orderId = order.path("orderId").asText();
        ResponseEntity<String> reconciled = post(
                "/api/orders/" + orderId + "/reconcile", null,
                Map.of(USER_HEADER, buyer.toString()));

        assertThat(reconciled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode reconciledOrder = parseBody(reconciled);
        assertThat(reconciledOrder.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(reconciledOrder.path("payment").path("paymentId").asText(null))
                .as("la orden persiste y expone el paymentId del cobro aprobado")
                .isNotBlank();
        assertThat(reconciledOrder.path("payment").path("status").asText())
                .isEqualTo("APPROVED");
        assertThat(reconciledOrder.path("items").get(0).path("tickets")).hasSize(1);

        // Efectos totales de la saga: 1 cobro, 1 reserva, 1 confirm, 1 emisión,
        // 0 release y 0 refund.
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments")).isEqualTo(1);
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release")).isZero();
        assertThat(countPostsWithSuffix(PAYMENT_REQUESTS, "/refund")).isZero();
        assertThat(countTicketIssuances()).isEqualTo(1);
    }
}
