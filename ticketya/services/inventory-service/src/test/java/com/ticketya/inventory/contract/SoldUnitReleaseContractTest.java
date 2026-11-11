package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class SoldUnitReleaseContractTest extends InventoryContractTestBase {

    @Test
    void releasesOneSeatAndOneGeneralUnitWithoutAffectingOtherSoldUnits() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID reservationId = confirmedSale(eventId, layout);

        assertThat(release(reservationId, layout.numberedSectorId(), layout.seatOneId(), 1)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(release(reservationId, layout.generalSectorId(), null, 1)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(release(reservationId, layout.generalSectorId(), null, 1))
                .path("alreadyReleased").asBoolean()).isTrue();

        JsonNode sectors = parseBody(getAvailability(eventId)).path("sectors");
        JsonNode numbered = findSector(sectors, "NUMBERED");
        assertThat(numbered.path("sold").asInt()).isZero();
        assertThat(numbered.path("seats").get(0).path("status").asText()).isEqualTo("AVAILABLE");
        JsonNode general = findSector(sectors, "GENERAL_ADMISSION");
        assertThat(general.path("sold").asInt()).isEqualTo(1);
        assertThat(general.path("available").asInt()).isEqualTo(2);
        assertThat(release(reservationId, layout.generalSectorId(), null, 2).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(findSector(parseBody(getAvailability(eventId)).path("sectors"),
                "GENERAL_ADMISSION").path("sold").asInt()).isZero();
    }

    @Test
    void foreignOrUnconfirmedUnitConflictsWithoutChangingAvailability() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID reservationId = confirmedSale(eventId, layout);
        assertProblem(release(reservationId, layout.numberedSectorId(), layout.seatTwoId(), 1),
                HttpStatus.CONFLICT);
        assertProblem(release(reservationId, layout.generalSectorId(), null, 3), HttpStatus.CONFLICT);
        assertThat(findSector(parseBody(getAvailability(eventId)).path("sectors"),
                "GENERAL_ADMISSION").path("sold").asInt()).isEqualTo(2);
    }

    @Test
    void concurrentReplayOnlyDecrementsOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID reservationId = confirmedSale(eventId, layout);
        CompletableFuture<ResponseEntity<String>> first = CompletableFuture.supplyAsync(
                () -> release(reservationId, layout.generalSectorId(), null, 1));
        CompletableFuture<ResponseEntity<String>> second = CompletableFuture.supplyAsync(
                () -> release(reservationId, layout.generalSectorId(), null, 1));
        assertThat(first.get().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.get().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(findSector(parseBody(getAvailability(eventId)).path("sectors"),
                "GENERAL_ADMISSION").path("sold").asInt()).isEqualTo(1);
    }

    private UUID confirmedSale(UUID eventId, Layout layout) {
        ResponseEntity<String> created = post("/internal/reservations", reservationBody(
                UUID.randomUUID(), eventId,
                seatItem(layout.numberedSectorId(), layout.seatOneId()) + "," +
                        quantityItem(layout.generalSectorId(), 2)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID reservationId = UUID.fromString(parseBody(created).path("reservationId").asText());
        assertThat(post("/internal/reservations/" + reservationId + "/confirm", null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        return reservationId;
    }

    private ResponseEntity<String> release(UUID reservationId, UUID sectorId, UUID seatId,
                                            int unitNumber) {
        String seat = seatId == null ? "null" : "\"" + seatId + "\"";
        return post("/internal/reservations/" + reservationId + "/sold-units/release",
                "{\"sectorId\":\"%s\",\"seatId\":%s,\"unitNumber\":%d}"
                        .formatted(sectorId, seat, unitNumber));
    }
}
