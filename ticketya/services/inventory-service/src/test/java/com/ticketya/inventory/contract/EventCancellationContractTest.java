package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class EventCancellationContractTest extends InventoryContractTestBase {

    @Test
    void cancellationBeforeInitializationBlocksReservationsAndIsIdempotent() {
        UUID eventId = UUID.randomUUID();
        String path = "/internal/events/" + eventId + "/cancellation";

        assertThat(put(path, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(put(path, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        quantityItem(UUID.randomUUID(), 1))).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancellationKeepsSoldPlacesUntilRefundAndRejectsNewReservations() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        String body = reservationBody(UUID.randomUUID(), eventId,
                quantityItem(layout.generalSectorId(), 1));
        JsonNode reservation = JSON.readTree(post("/internal/reservations", body).getBody());
        UUID reservationId = UUID.fromString(reservation.path("reservationId").asText());
        assertThat(post("/internal/reservations/" + reservationId + "/confirm", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(put("/internal/events/" + eventId + "/cancellation", null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        quantityItem(layout.generalSectorId(), 1))).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        JsonNode sectors = JSON.readTree(getAvailability(eventId).getBody()).path("sectors");
        assertThat(findSector(sectors, "GENERAL_ADMISSION").path("sold").asInt()).isEqualTo(1);
    }

    @Test
    void concurrentCancellationAndReservationCannotOpenSalesAfterCancellation() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Integer> reserve = CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
                return post("/internal/reservations", reservationBody(UUID.randomUUID(), eventId,
                        quantityItem(layout.generalSectorId(), 1))).getStatusCode().value();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
        CompletableFuture<Integer> cancel = CompletableFuture.supplyAsync(() -> {
            try {
                start.await();
                return put("/internal/events/" + eventId + "/cancellation", null).getStatusCode().value();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
        start.countDown();
        assertThat(cancel.get()).isEqualTo(204);
        assertThat(reserve.get()).isIn(201, 409);
        assertThat(post("/internal/reservations", reservationBody(UUID.randomUUID(), eventId,
                quantityItem(layout.generalSectorId(), 1))).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
