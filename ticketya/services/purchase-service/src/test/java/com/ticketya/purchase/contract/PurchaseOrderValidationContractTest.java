package com.ticketya.purchase.contract;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de validación de `POST /api/orders`: payloads inválidos (400),
 * identidad ausente o inválida (403) y evento no comprable (404), siempre sin
 * reservar ni cobrar.
 */
@Testcontainers(disabledWithoutDocker = true)
class PurchaseOrderValidationContractTest extends PurchaseContractTestBase {

    private final UUID buyer = UUID.randomUUID();

    @Test
    void itemWithBothSeatIdsAndQuantityReturns400() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String item = "{\"sectorId\":\"%s\",\"seatIds\":[\"%s\"],\"quantity\":1}"
                .formatted(layout.plateaSectorId(), layout.seatOneId());

        Map<String, String> headers = buyerHeaders(buyer, "key-both-1");
        headers.put(CORRELATION_HEADER, "trace-purchase-400");
        ResponseEntity<String> response = createOrder(
                orderBody(eventId, item, "CARD", "tok_approved"), headers);

        assertProblem(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getFirst(CORRELATION_HEADER))
                .isEqualTo("trace-purchase-400");
        assertNoSideEffects();
    }

    @Test
    void itemWithNeitherSeatIdsNorQuantityReturns400() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String item = "{\"sectorId\":\"%s\"}".formatted(layout.plateaSectorId());

        ResponseEntity<String> response = createOrder(
                orderBody(eventId, item, "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-none-1"));

        assertProblem(response, HttpStatus.BAD_REQUEST);
        assertNoSideEffects();
    }

    @Test
    void nonPositiveQuantityReturns400() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);

        ResponseEntity<String> zero = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 0),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-zero-1"));
        assertProblem(zero, HttpStatus.BAD_REQUEST);

        ResponseEntity<String> negative = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), -1),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-negative-1"));
        assertProblem(negative, HttpStatus.BAD_REQUEST);
        assertNoSideEffects();
    }

    @Test
    void seatBelongingToAnotherSectorReturns400() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        // Asiento de Platea informado en el sector de admisión general Campo.
        String item = seatItem(layout.campoSectorId(), layout.seatOneId());

        ResponseEntity<String> response = createOrder(
                orderBody(eventId, item, "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-foreign-seat-1"));

        assertProblem(response, HttpStatus.BAD_REQUEST);
        assertNoSideEffects();
    }

    @Test
    void duplicatedItemReturns400() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String items = quantityItem(layout.campoSectorId(), 1)
                + "," + quantityItem(layout.campoSectorId(), 1);

        ResponseEntity<String> response = createOrder(
                orderBody(eventId, items, "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-duplicate-1"));

        assertProblem(response, HttpStatus.BAD_REQUEST);
        assertNoSideEffects();
    }

    @Test
    void missingIdempotencyKeyReturns400() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");

        ResponseEntity<String> response = post("/api/orders", body,
                Map.of(USER_HEADER, buyer.toString()));

        assertProblem(response, HttpStatus.BAD_REQUEST);
        assertNoSideEffects();
    }

    @Test
    void missingUserIdReturns403() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");

        ResponseEntity<String> response = post("/api/orders", body,
                Map.of(IDEMPOTENCY_HEADER, "key-no-user-1"));

        assertProblem(response, HttpStatus.FORBIDDEN);
        assertNoSideEffects();
    }

    @Test
    void nonUuidUserIdReturns403() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");

        ResponseEntity<String> response = post("/api/orders", body,
                Map.of(USER_HEADER, "not-a-uuid", IDEMPOTENCY_HEADER, "key-bad-user-1"));

        assertProblem(response, HttpStatus.FORBIDDEN);
        assertNoSideEffects();
    }

    @Test
    void invalidUserRoleReturns403() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");

        Map<String, String> headers = buyerHeaders(buyer, "key-bad-role-1");
        headers.put(ROLE_HEADER, "SUPERVISOR");
        ResponseEntity<String> response = createOrder(body, headers);

        assertProblem(response, HttpStatus.FORBIDDEN);
        assertNoSideEffects();
    }

    @Test
    void notPurchasableEventReturns404WithoutReservingOrCharging() {
        UUID eventId = NOT_PURCHASABLE_EVENT;
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");

        ResponseEntity<String> response = createOrder(body,
                buyerHeaders(buyer, "key-not-purchasable-1"));

        assertProblem(response, HttpStatus.NOT_FOUND);
        // Se consultó el snapshot de compra, pero no se reservó ni se cobró.
        assertThat(CATALOG_REQUESTS)
                .anyMatch(request -> request.path().contains("/purchase-snapshot"));
        assertNoSideEffects();
    }

    // ------------------------------------------------------------------ helpers

    /** Ninguna validación fallida debe reservar, cobrar ni emitir tickets. */
    private static void assertNoSideEffects() {
        assertThat(INVENTORY_REQUESTS)
                .as("no se reservó inventario")
                .isEmpty();
        assertThat(PAYMENT_REQUESTS)
                .as("no se invocó Payment")
                .isEmpty();
        assertThat(TICKET_REQUESTS)
                .as("no se emitieron tickets")
                .isEmpty();
    }
}
