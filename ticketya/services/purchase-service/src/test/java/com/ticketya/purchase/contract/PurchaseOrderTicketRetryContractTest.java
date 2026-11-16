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
 * Tarea 3.3 (2): Ticket Service falla transitoriamente al emitir y luego se
 * recupera. La primera compra queda 202/PAID_PENDING_TICKETS, la
 * reconciliación devuelve 200/CONFIRMED y no hay recobro, re-reserva ni
 * tickets duplicados.
 */
@Testcontainers(disabledWithoutDocker = true)
class PurchaseOrderTicketRetryContractTest extends PurchaseContractTestBase {

    private final UUID buyer = UUID.randomUUID();

    @Test
    void transientTicketFailureIsRecoveredByReconcileWithoutDuplicatedEffects() {
        UUID eventId = TICKET_FLAKY_EVENT;
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");

        ResponseEntity<String> first = createOrder(body,
                buyerHeaders(buyer, "key-ticket-retry-1"));

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode pending = parseBody(first);
        String orderId = pending.path("orderId").asText();
        assertThat(pending.path("status").asText()).isEqualTo("PAID_PENDING_TICKETS");
        assertThat(pending.path("payment").path("status").asText()).isEqualTo("APPROVED");
        assertThat(pending.path("reservationExpiresAt").isNull())
                .as("una orden pagada ya no depende de una reserva vigente")
                .isTrue();
        for (JsonNode item : pending.path("items")) {
            assertThat(item.path("tickets"))
                    .as("sin emisión exitosa no hay tickets expuestos")
                    .isEmpty();
        }

        // El intento de emisión falló con 503 del stub de Ticket.
        assertThat(countTicketIssuances()).isEqualTo(1);
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments")).isEqualTo(1);
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations")).isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release")).isZero();

        // Reconciliación: completa la emisión sin volver a cobrar ni re-reservar.
        ResponseEntity<String> reconciled = post("/api/orders/" + orderId + "/reconcile",
                null, Map.of(USER_HEADER, buyer.toString()));

        assertThat(reconciled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode confirmed = parseBody(reconciled);
        assertThat(confirmed.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmed.path("items")).hasSize(1);
        JsonNode tickets = confirmed.path("items").get(0).path("tickets");
        assertThat(tickets)
                .as("exactamente un ticket por unidad, sin duplicados")
                .hasSize(1);
        assertThat(tickets.get(0).path("ticketId").asText(null)).isNotBlank();
        assertThat(tickets.get(0).path("qrPayload").asText(null)).isNotBlank();

        // Efectos de la saga completa: 1 cobro, 1 reserva, 0 liberaciones,
        // confirm original + reconfirmación idempotente al reconciliar y
        // 2 intentos de emisión (1 fallido + 1 exitoso).
        assertThat(countPosts(PAYMENT_REQUESTS, "/internal/payments"))
                .as("no se vuelve a cobrar en la reconciliación")
                .isEqualTo(1);
        assertThat(countPostsWithSuffix(PAYMENT_REQUESTS, "/refund")).isZero();
        assertThat(countPosts(INVENTORY_REQUESTS, "/internal/reservations"))
                .as("no se vuelve a reservar en la reconciliación")
                .isEqualTo(1);
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release")).isZero();
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm")).isEqualTo(2);
        assertThat(countTicketIssuances()).isEqualTo(2);

        // La orden persistida queda CONFIRMED con su ticket.
        ResponseEntity<String> fetched = get("/api/orders/" + orderId,
                Map.of(USER_HEADER, buyer.toString()));
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = parseBody(fetched);
        assertThat(order.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(order.path("items").get(0).path("tickets")).hasSize(1);
    }
}
