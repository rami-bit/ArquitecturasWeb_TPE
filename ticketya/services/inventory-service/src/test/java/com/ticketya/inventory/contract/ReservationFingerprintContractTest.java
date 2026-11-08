package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fingerprint canónico del payload: el orden de los ítems no altera la idempotencia, pero
 * un payload distinto con el mismo `orderId` sí es un conflicto.
 */
@org.testcontainers.junit.jupiter.Testcontainers(disabledWithoutDocker = true)
class ReservationFingerprintContractTest extends InventoryContractTestBase {

    @Test
    void itemOrderDoesNotChangeTheIdempotentFingerprint() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID orderId = UUID.randomUUID();

        String seat = seatItem(layout.numberedSectorId(), layout.seatOneId());
        String general = quantityItem(layout.generalSectorId(), 2);

        var created = post("/internal/reservations",
                reservationBody(orderId, eventId, seat + "," + general));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID reservationId = UUID.fromString(
                parseBody(created).path("reservationId").asText());

        // Mismo payload semántico con los ítems en orden inverso: replay idempotente.
        var replayed = post("/internal/reservations",
                reservationBody(orderId, eventId, general + "," + seat));
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode replay = parseBody(replayed);
        assertThat(replay.path("reservationId").asText())
                .isEqualTo(reservationId.toString());
        assertThat(replay.path("items")).hasSize(2);

        // Mismo orderId con otro contenido: conflicto, sin tocar la reserva original.
        var conflicting = post("/internal/reservations",
                reservationBody(orderId, eventId,
                        seatItem(layout.numberedSectorId(), layout.seatTwoId())));
        assertProblem(conflicting, HttpStatus.CONFLICT);

        assertThat(jdbc().queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
    }
}
