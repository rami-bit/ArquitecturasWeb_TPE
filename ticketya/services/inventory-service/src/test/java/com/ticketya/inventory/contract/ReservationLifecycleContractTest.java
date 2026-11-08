package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato del ciclo de vida de reservas: confirm y release (idempotencia, conflictos
 * de estado final, vencimiento y liberación perezosa).
 */
@Testcontainers(disabledWithoutDocker = true)
class ReservationLifecycleContractTest extends InventoryContractTestBase {

    @Test
    void confirmingActiveReservationSellsSeatAndGeneralAdmission() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID reservationId = createActiveReservationWithSeatAndGeneral(eventId, layout);

        ResponseEntity<String> confirm =
                post("/internal/reservations/" + reservationId + "/confirm", null);

        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(confirm).path("status").asText()).isEqualTo("CONFIRMED");

        JsonNode sectors = parseBody(getAvailability(eventId)).path("sectors");
        JsonNode numbered = findSector(sectors, "NUMBERED");
        assertThat(seatWithId(numbered, layout.seatOneId()).path("status").asText())
                .isEqualTo("SOLD");
        assertThat(numbered.path("sold").asInt()).isEqualTo(1);
        assertThat(numbered.path("reserved").asInt()).isEqualTo(0);

        JsonNode general = findSector(sectors, "GENERAL_ADMISSION");
        assertThat(general.path("sold").asInt()).isEqualTo(2);
        assertThat(general.path("reserved").asInt()).isEqualTo(0);
        assertThat(general.path("available").asInt()).isEqualTo(1);
    }

    @Test
    void repeatingConfirmationIsIdempotentAndDoesNotDuplicateSold() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID reservationId = createActiveReservationWithSeatAndGeneral(eventId, layout);

        assertThat(post("/internal/reservations/" + reservationId + "/confirm", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode sectorsAfterFirst = parseBody(getAvailability(eventId)).path("sectors");

        ResponseEntity<String> repeated =
                post("/internal/reservations/" + reservationId + "/confirm", null);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(repeated).path("status").asText()).isEqualTo("CONFIRMED");

        JsonNode sectorsAfterSecond = parseBody(getAvailability(eventId)).path("sectors");
        assertThat(findSector(sectorsAfterSecond, "NUMBERED").path("sold").asInt())
                .isEqualTo(findSector(sectorsAfterFirst, "NUMBERED").path("sold").asInt())
                .isEqualTo(1);
        assertThat(findSector(sectorsAfterSecond, "GENERAL_ADMISSION").path("sold").asInt())
                .isEqualTo(findSector(sectorsAfterFirst, "GENERAL_ADMISSION").path("sold").asInt())
                .isEqualTo(2);
        assertThat(findSector(sectorsAfterSecond, "GENERAL_ADMISSION").path("reserved").asInt())
                .isEqualTo(0);
    }

    @Test
    void confirmingExpiredReservationConflictsAndAvailabilityFreesTheSeat() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID reservationId = createSeatOnlyReservation(eventId, layout);

        forceReservationExpiry(reservationId);

        ResponseEntity<String> confirm =
                post("/internal/reservations/" + reservationId + "/confirm", null);
        assertProblem(confirm, HttpStatus.CONFLICT);

        // La disponibilidad libera el asiento de la reserva vencida.
        JsonNode numbered = findSector(
                parseBody(getAvailability(eventId)).path("sectors"), "NUMBERED");
        assertThat(seatWithId(numbered, layout.seatOneId()).path("status").asText())
                .isEqualTo("AVAILABLE");
        assertThat(numbered.path("reserved").asInt()).isEqualTo(0);
        assertThat(numbered.path("sold").asInt()).isEqualTo(0);
    }

    @Test
    void releasingActiveReservationFreesSeatAndGeneralAdmission() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID reservationId = createActiveReservationWithSeatAndGeneral(eventId, layout);

        ResponseEntity<String> release =
                post("/internal/reservations/" + reservationId + "/release", null);

        assertThat(release.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(release).path("status").asText()).isEqualTo("RELEASED");

        JsonNode sectors = parseBody(getAvailability(eventId)).path("sectors");
        JsonNode numbered = findSector(sectors, "NUMBERED");
        assertThat(seatWithId(numbered, layout.seatOneId()).path("status").asText())
                .isEqualTo("AVAILABLE");
        assertThat(numbered.path("reserved").asInt()).isEqualTo(0);
        JsonNode general = findSector(sectors, "GENERAL_ADMISSION");
        assertThat(general.path("reserved").asInt()).isEqualTo(0);
        assertThat(general.path("available").asInt()).isEqualTo(3);
    }

    @Test
    void repeatingReleaseIsIdempotentWithoutDoubleRelease() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID reservationId = createActiveReservationWithSeatAndGeneral(eventId, layout);

        assertThat(post("/internal/reservations/" + reservationId + "/release", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> repeated =
                post("/internal/reservations/" + reservationId + "/release", null);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(repeated).path("status").asText()).isEqualTo("RELEASED");

        // Sin doble liberación: la disponibilidad sigue en su máximo.
        JsonNode sectors = parseBody(getAvailability(eventId)).path("sectors");
        assertThat(findSector(sectors, "NUMBERED").path("available").asInt()).isEqualTo(2);
        assertThat(findSector(sectors, "GENERAL_ADMISSION").path("available").asInt())
                .isEqualTo(3);
    }

    @Test
    void releasingConfirmedReservationConflictsAndKeepsSold() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID reservationId = createActiveReservationWithSeatAndGeneral(eventId, layout);
        assertThat(post("/internal/reservations/" + reservationId + "/confirm", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> release =
                post("/internal/reservations/" + reservationId + "/release", null);
        assertProblem(release, HttpStatus.CONFLICT);

        JsonNode sectors = parseBody(getAvailability(eventId)).path("sectors");
        assertThat(seatWithId(findSector(sectors, "NUMBERED"), layout.seatOneId())
                .path("status").asText()).isEqualTo("SOLD");
        assertThat(findSector(sectors, "GENERAL_ADMISSION").path("sold").asInt()).isEqualTo(2);
    }

    @Test
    void confirmingReleasedReservationConflicts() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID reservationId = createActiveReservationWithSeatAndGeneral(eventId, layout);
        assertThat(post("/internal/reservations/" + reservationId + "/release", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> confirm =
                post("/internal/reservations/" + reservationId + "/confirm", null);
        assertProblem(confirm, HttpStatus.CONFLICT);

        JsonNode sectors = parseBody(getAvailability(eventId)).path("sectors");
        assertThat(seatWithId(findSector(sectors, "NUMBERED"), layout.seatOneId())
                .path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(findSector(sectors, "GENERAL_ADMISSION").path("sold").asInt()).isEqualTo(0);
    }

    @Test
    void releasingExpiredReservationReturnsFinalStateWithoutTouchingSold() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID reservationId = createActiveReservationWithSeatAndGeneral(eventId, layout);

        forceReservationExpiry(reservationId);
        // El GET de disponibilidad ejerce la expiración perezosa.
        JsonNode sectors = parseBody(getAvailability(eventId)).path("sectors");
        assertThat(seatWithId(findSector(sectors, "NUMBERED"), layout.seatOneId())
                .path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(findSector(sectors, "GENERAL_ADMISSION").path("sold").asInt()).isEqualTo(0);

        ResponseEntity<String> release =
                post("/internal/reservations/" + reservationId + "/release", null);
        assertThat(release.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(release).path("status").asText()).isEqualTo("EXPIRED");

        // La liberación de una reserva vencida no toca lugares vendidos.
        JsonNode afterRelease = parseBody(getAvailability(eventId)).path("sectors");
        assertThat(seatWithId(findSector(afterRelease, "NUMBERED"), layout.seatOneId())
                .path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(findSector(afterRelease, "NUMBERED").path("sold").asInt()).isEqualTo(0);
        assertThat(findSector(afterRelease, "GENERAL_ADMISSION").path("sold").asInt())
                .isEqualTo(0);
        assertThat(findSector(afterRelease, "GENERAL_ADMISSION").path("available").asInt())
                .isEqualTo(3);
    }

    @Test
    void confirmAndReleaseOfUnknownReservationReturnProblemJson404() {
        UUID unknownReservation = UUID.randomUUID();

        assertProblem(post("/internal/reservations/" + unknownReservation + "/confirm", null),
                HttpStatus.NOT_FOUND);
        assertProblem(post("/internal/reservations/" + unknownReservation + "/release", null),
                HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------------ utils

    private UUID createActiveReservationWithSeatAndGeneral(UUID eventId, Layout layout)
            throws Exception {
        ResponseEntity<String> response = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId()) + ","
                                + quantityItem(layout.generalSectorId(), 2)));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(parseBody(response).path("reservationId").asText());
    }

    private UUID createSeatOnlyReservation(UUID eventId, Layout layout) throws Exception {
        ResponseEntity<String> response = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(parseBody(response).path("reservationId").asText());
    }

    private static JsonNode seatWithId(JsonNode numberedSector, UUID seatId) {
        for (JsonNode seat : numberedSector.path("seats")) {
            if (seatId.toString().equals(seat.path("seatId").asText(null))) {
                return seat;
            }
        }
        throw new AssertionError("asiento " + seatId + " no encontrado");
    }
}
