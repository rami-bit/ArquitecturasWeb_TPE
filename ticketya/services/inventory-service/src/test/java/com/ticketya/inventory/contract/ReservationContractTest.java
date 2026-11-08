package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de `POST /internal/reservations`: creación, idempotencia, atomicidad,
 * validación de payloads, errores de evento y TTL explícito.
 */
@Testcontainers(disabledWithoutDocker = true)
class ReservationContractTest extends InventoryContractTestBase {

    @Test
    void createsSeatReservationWithCoherentResponse() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID orderId = UUID.randomUUID();

        ResponseEntity<String> response = post("/internal/reservations",
                reservationBody(orderId, eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode reservation = parseBody(response);
        UUID reservationId = UUID.fromString(reservation.path("reservationId").asText());
        assertThat(reservationId).isNotNull();
        assertThat(reservation.path("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(reservation.path("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(reservation.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(reservation.path("createdAt").asText(null)).isNotBlank();
        assertThat(reservation.path("expiresAt").asText(null)).isNotBlank();
        assertThat(java.time.OffsetDateTime.parse(reservation.path("expiresAt").asText())
                .isAfter(java.time.OffsetDateTime.parse(reservation.path("createdAt").asText())))
                .isTrue();
        JsonNode items = reservation.path("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("sectorId").asText())
                .isEqualTo(layout.numberedSectorId().toString());
        assertThat(items.get(0).path("seatId").asText())
                .isEqualTo(layout.seatOneId().toString());
        assertThat(items.get(0).path("quantity").asInt()).isEqualTo(1);
    }

    @Test
    void repeatingIdenticalReservationReturnsTheSameOneWithoutHoldingTwice() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID orderId = UUID.randomUUID();
        String body = reservationBody(orderId, eventId,
                seatItem(layout.numberedSectorId(), layout.seatOneId()));

        ResponseEntity<String> created = post("/internal/reservations", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode first = parseBody(created);

        ResponseEntity<String> repeated = post("/internal/reservations", body);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode second = parseBody(repeated);

        assertThat(second.path("reservationId").asText())
                .isEqualTo(first.path("reservationId").asText());
        assertThat(second.path("expiresAt").asText())
                .isEqualTo(first.path("expiresAt").asText());

        // La disponibilidad no retiene dos veces el mismo asiento.
        JsonNode sectors = parseBody(getAvailability(eventId)).path("sectors");
        JsonNode numbered = findSector(sectors, "NUMBERED");
        assertThat(numbered.path("reserved").asInt()).isEqualTo(1);
        assertThat(numbered.path("available").asInt()).isEqualTo(1);
        assertThat(seatsWithStatus(numbered, "HELD")).isEqualTo(1);
    }

    @Test
    void sameOrderIdWithDifferentItemsConflictsAndKeepsTheOriginalReservation() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID orderId = UUID.randomUUID();

        ResponseEntity<String> created = post("/internal/reservations",
                reservationBody(orderId, eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode original = parseBody(created);

        ResponseEntity<String> conflicting = post("/internal/reservations",
                reservationBody(orderId, eventId,
                        seatItem(layout.numberedSectorId(), layout.seatTwoId())));

        assertProblem(conflicting, HttpStatus.CONFLICT);

        // La reserva original no fue alterada.
        ResponseEntity<String> byOrder =
                get("/internal/reservations/by-order/" + orderId);
        assertThat(byOrder.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode unchanged = parseBody(byOrder);
        assertThat(unchanged.path("reservationId").asText())
                .isEqualTo(original.path("reservationId").asText());
        assertThat(unchanged.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(unchanged.path("items")).hasSize(1);
        assertThat(unchanged.path("items").get(0).path("seatId").asText())
                .isEqualTo(layout.seatOneId().toString());
    }

    @Test
    void multiSeatReservationRollsBackCompletelyWhenOneSeatIsAlreadyHeld() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        ResponseEntity<String> firstHold = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));
        assertThat(firstHold.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID secondOrder = UUID.randomUUID();
        ResponseEntity<String> conflict = post("/internal/reservations",
                reservationBody(secondOrder, eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId()) + ","
                                + seatItem(layout.numberedSectorId(), layout.seatTwoId())));
        assertProblem(conflict, HttpStatus.CONFLICT);

        // Rollback total: el segundo asiento sigue disponible y no queda reserva parcial.
        JsonNode numbered = findSector(
                parseBody(getAvailability(eventId)).path("sectors"), "NUMBERED");
        assertThat(seatWithId(numbered, layout.seatTwoId()).path("status").asText())
                .isEqualTo("AVAILABLE");
        assertThat(seatWithId(numbered, layout.seatOneId()).path("status").asText())
                .isEqualTo("HELD");

        ResponseEntity<String> byOrder = get("/internal/reservations/by-order/" + secondOrder);
        assertThat(byOrder.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void generalAdmissionReservationDecrementsAvailability() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        ResponseEntity<String> response = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        quantityItem(layout.generalSectorId(), 2)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode general = findSector(
                parseBody(getAvailability(eventId)).path("sectors"), "GENERAL_ADMISSION");
        assertThat(general.path("sold").asInt()).isEqualTo(0);
        assertThat(general.path("reserved").asInt()).isEqualTo(2);
        assertThat(general.path("available").asInt()).isEqualTo(1);
    }

    @Test
    void generalAdmissionReservationAboveRemainingCapacityConflicts() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        ResponseEntity<String> first = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        quantityItem(layout.generalSectorId(), 1)));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Quedan 2 de 3 cupos: pedir 3 debe rechazarse sin alterar nada.
        ResponseEntity<String> overbook = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        quantityItem(layout.generalSectorId(), 3)));
        assertProblem(overbook, HttpStatus.CONFLICT);

        JsonNode general = findSector(
                parseBody(getAvailability(eventId)).path("sectors"), "GENERAL_ADMISSION");
        assertThat(general.path("reserved").asInt()).isEqualTo(1);
        assertThat(general.path("available").asInt()).isEqualTo(2);
    }

    @Test
    void invalidPayloadsReturnProblemJson400WithoutTouchingInventory() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID orderId = UUID.randomUUID();

        assertProblem(post("/internal/reservations",
                        reservationBody(orderId, eventId,
                                quantityItem(UUID.randomUUID(), 1))),
                HttpStatus.BAD_REQUEST);

        assertProblem(post("/internal/reservations",
                        reservationBody(orderId, eventId,
                                "{\"sectorId\":\"%s\",\"seatId\":\"%s\"}".formatted(
                                        layout.generalSectorId(), layout.seatOneId()))),
                HttpStatus.BAD_REQUEST);

        assertProblem(post("/internal/reservations",
                        reservationBody(orderId, eventId,
                                "{\"sectorId\":\"%s\",\"seatId\":\"%s\",\"quantity\":1}"
                                        .formatted(layout.numberedSectorId(),
                                                layout.seatOneId()))),
                HttpStatus.BAD_REQUEST);

        assertProblem(post("/internal/reservations",
                        reservationBody(orderId, eventId,
                                quantityItem(layout.generalSectorId(), 0))),
                HttpStatus.BAD_REQUEST);

        assertProblem(post("/internal/reservations",
                        reservationBody(orderId, eventId, "")),
                HttpStatus.BAD_REQUEST);

        assertProblem(post("/internal/reservations",
                        "{\"eventId\":\"%s\",\"items\":[%s]}".formatted(
                                eventId, seatItem(layout.numberedSectorId(),
                                        layout.seatOneId()))),
                HttpStatus.BAD_REQUEST);

        assertProblem(post("/internal/reservations",
                        "{\"orderId\":\"%s\",\"items\":[%s]}".formatted(
                                orderId, seatItem(layout.numberedSectorId(),
                                        layout.seatOneId()))),
                HttpStatus.BAD_REQUEST);

        // El inventario no quedó afectado por los payloads inválidos.
        JsonNode numbered = findSector(
                parseBody(getAvailability(eventId)).path("sectors"), "NUMBERED");
        assertThat(numbered.path("reserved").asInt()).isEqualTo(0);
        assertThat(numbered.path("available").asInt()).isEqualTo(2);
    }

    @Test
    void unknownEventReturnsProblemJson404() {
        ResponseEntity<String> response = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), UNKNOWN_EVENT,
                        "{\"sectorId\":\"%s\",\"quantity\":1}".formatted(UUID.randomUUID())));

        assertProblem(response, HttpStatus.NOT_FOUND);
    }

    @Test
    void catalogUnavailableDuringInitializationReturnsProblemJson503() {
        ResponseEntity<String> response = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), CATALOG_DOWN_EVENT,
                        "{\"sectorId\":\"%s\",\"quantity\":1}".formatted(UUID.randomUUID())));

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void explicitTtlSecondsDrivesTheExpirationWindow() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        ResponseEntity<String> response = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId()), 120));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long ttl = ttlSeconds(parseBody(response));
        assertThat(Math.abs(ttl - 120))
                .as("expiresAt - createdAt debe ser 120s ±10s, fue %s", ttl)
                .isLessThanOrEqualTo(10);
    }

    // ------------------------------------------------------------------------ utils

    private static int seatsWithStatus(JsonNode numberedSector, String status) {
        int count = 0;
        for (JsonNode seat : numberedSector.path("seats")) {
            if (status.equals(seat.path("status").asText(null))) {
                count++;
            }
        }
        return count;
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
