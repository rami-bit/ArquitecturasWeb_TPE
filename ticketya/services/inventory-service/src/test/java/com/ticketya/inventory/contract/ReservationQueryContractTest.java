package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de lectura de reservas: `GET /internal/reservations/by-order/{orderId}` y
 * `GET /internal/reservations/{reservationId}`, incluidos los 404 de recursos inexistentes.
 */
@Testcontainers(disabledWithoutDocker = true)
class ReservationQueryContractTest extends InventoryContractTestBase {

    @Test
    void fetchesTheCreatedReservationByOrderId() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID orderId = UUID.randomUUID();

        ResponseEntity<String> created = post("/internal/reservations",
                reservationBody(orderId, eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode reservation = parseBody(created);

        ResponseEntity<String> byOrder =
                get("/internal/reservations/by-order/" + orderId);

        assertThat(byOrder.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byOrder.getHeaders().getFirst(CORRELATION_HEADER)).isNotBlank();
        JsonNode queried = parseBody(byOrder);
        assertThat(queried.path("reservationId").asText())
                .isEqualTo(reservation.path("reservationId").asText());
        assertThat(queried.path("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(queried.path("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(queried.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(queried.path("items")).hasSize(1);
        assertThat(queried.path("items").get(0).path("seatId").asText())
                .isEqualTo(layout.seatOneId().toString());
    }

    @Test
    void fetchesTheCreatedReservationById() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        ResponseEntity<String> created = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode reservation = parseBody(created);
        String reservationId = reservation.path("reservationId").asText();

        ResponseEntity<String> byId = get("/internal/reservations/" + reservationId);

        assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode queried = parseBody(byId);
        assertThat(queried.path("reservationId").asText()).isEqualTo(reservationId);
        assertThat(queried.path("status").asText())
                .isEqualTo(reservation.path("status").asText());
        assertThat(queried.path("expiresAt").asText())
                .isEqualTo(reservation.path("expiresAt").asText());
        assertThat(queried.path("items")).hasSize(1);
    }

    @Test
    void unknownOrderIdReturnsProblemJson404() {
        ResponseEntity<String> response =
                get("/internal/reservations/by-order/" + UUID.randomUUID());

        assertProblem(response, HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownReservationIdReturnsProblemJson404() {
        ResponseEntity<String> response =
                get("/internal/reservations/" + UUID.randomUUID());

        assertProblem(response, HttpStatus.NOT_FOUND);
    }
}
