package com.ticketya.purchase.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 3.3 (4): fallas 503 configurables de Catalog, Inventory y Payment.
 * Purchase responde 503 como problema (problem+json con correlation ID), no
 * deja una orden confirmada parcialmente y no cobra ni emite tickets.
 */
@Testcontainers(disabledWithoutDocker = true)
class PurchaseOrderDownstreamFailureContractTest extends PurchaseContractTestBase {

    private final UUID buyer = UUID.randomUUID();

    @AfterEach
    void clearFaults() {
        clearInjectedFaults();
    }

    @Test
    void catalogOutageReturns503WithoutCreatingTheOrder() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");
        Map<String, String> headers = buyerHeaders(buyer, "key-catalog-503");

        failCatalogSnapshot();
        ResponseEntity<String> response = createOrder(body, headers);

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode problem = parseBody(response);
        assertThat(problem.path("detail").asText()).contains("Catalog");

        // Sin reserva, cobro, confirmación ni emisión.
        assertThat(INVENTORY_REQUESTS).isEmpty();
        assertThat(PAYMENT_REQUESTS).isEmpty();
        assertThat(TICKET_REQUESTS).isEmpty();

        // No quedó orden persistida: la misma clave y contenido crean una orden nueva.
        clearInjectedFaults();
        ResponseEntity<String> retry = createOrder(body, headers);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(parseBody(retry).path("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void inventoryAvailabilityOutageReturns503WithoutCreatingTheOrder() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");
        Map<String, String> headers = buyerHeaders(buyer, "key-availability-503");

        failInventoryAvailability();
        ResponseEntity<String> response = createOrder(body, headers);

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(PAYMENT_REQUESTS)
                .as("sin disponibilidad no se invoca Payment")
                .isEmpty();
        assertThat(TICKET_REQUESTS).isEmpty();

        // No quedó orden ni consumo de límite: la misma compra vuelve a entrar.
        clearInjectedFaults();
        ResponseEntity<String> retry = createOrder(body, headers);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(parseBody(retry).path("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void inventoryReserveOutageReturns503WithoutCharging() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");
        Map<String, String> headers = buyerHeaders(buyer, "key-reserve-503");

        failInventoryReserve();
        ResponseEntity<String> response = createOrder(body, headers);

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(PAYMENT_REQUESTS)
                .as("si la reserva falla no se invoca Payment")
                .isEmpty();
        assertThat(TICKET_REQUESTS).isEmpty();
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm")).isZero();

        // El límite quedó liberado: 6 unidades vuelven a entrar para el evento.
        clearInjectedFaults();
        ResponseEntity<String> retry = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 6),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-reserve-503-retry"));
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(parseBody(retry).path("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void paymentChargeOutageReturns503AndReleasesReservationAndLimit() {
        UUID eventId = UUID.randomUUID();
        Layout layout = layoutFor(eventId);
        String body = orderBody(eventId, quantityItem(layout.campoSectorId(), 1),
                "CARD", "tok_approved");
        Map<String, String> headers = buyerHeaders(buyer, "key-charge-503");

        failPaymentCharge();
        ResponseEntity<String> response = createOrder(body, headers);

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(TICKET_REQUESTS)
                .as("sin cobro no se emiten tickets")
                .isEmpty();
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/confirm"))
                .as("sin cobro no se confirma el inventario")
                .isZero();
        assertThat(countPostsWithSuffix(INVENTORY_REQUESTS, "/release"))
                .as("la reserva retenida se libera")
                .isEqualTo(1);

        // El límite quedó liberado: 6 unidades vuelven a entrar para el evento.
        clearInjectedFaults();
        ResponseEntity<String> retry = createOrder(
                orderBody(eventId, quantityItem(layout.campoSectorId(), 6),
                        "CARD", "tok_approved"),
                buyerHeaders(buyer, "key-charge-503-retry"));
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(parseBody(retry).path("status").asText()).isEqualTo("CONFIRMED");
    }
}
