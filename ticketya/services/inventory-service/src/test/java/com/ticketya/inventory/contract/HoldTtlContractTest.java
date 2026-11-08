package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de `PUT /internal/events/{eventId}/hold-ttl`: TTL por defecto (600 s),
 * configuración por evento, rechazo fuera de rango y conservación del valor anterior.
 */
@Testcontainers(disabledWithoutDocker = true)
class HoldTtlContractTest extends InventoryContractTestBase {

    @Test
    void defaultEventHoldTtlIsSixHundredSeconds() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        ResponseEntity<String> reservation = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));

        assertThat(reservation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long ttl = ttlSeconds(parseBody(reservation));
        assertThat(Math.abs(ttl - 600))
                .as("TTL por defecto 600s ±10s, fue %s", ttl)
                .isLessThanOrEqualTo(10);
    }

    @Test
    void configuredHoldTtlDrivesLaterReservations() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        ResponseEntity<String> configured = putHoldTtl(eventId, "{\"ttlSeconds\":300}");
        assertThat(configured.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode configuration = parseBody(configured);
        assertThat(configuration.path("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(configuration.path("holdTtlSeconds").asInt()).isEqualTo(300);

        ResponseEntity<String> reservation = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));
        assertThat(reservation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long ttl = ttlSeconds(parseBody(reservation));
        assertThat(Math.abs(ttl - 300))
                .as("TTL configurado 300s ±10s, fue %s", ttl)
                .isLessThanOrEqualTo(10);
    }

    @Test
    void outOfRangeHoldTtlIsRejectedAndKeepsThePreviousValue() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        assertThat(putHoldTtl(eventId, "{\"ttlSeconds\":300}").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertProblem(putHoldTtl(eventId, "{\"ttlSeconds\":59}"), HttpStatus.BAD_REQUEST);
        assertProblem(putHoldTtl(eventId, "{\"ttlSeconds\":3601}"), HttpStatus.BAD_REQUEST);

        ResponseEntity<String> reservation = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));
        assertThat(reservation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long ttl = ttlSeconds(parseBody(reservation));
        assertThat(Math.abs(ttl - 300))
                .as("se conserva el valor anterior (300s ±10s), fue %s", ttl)
                .isLessThanOrEqualTo(10);
    }

    @Test
    void holdTtlForEventWithoutMaterializedInventoryReturnsProblemJson404() {
        // UUID nuevo sin inicializar: no se consulta Catalog ni se crea inventario.
        ResponseEntity<String> response = putHoldTtl(UUID.randomUUID(), "{\"ttlSeconds\":300}");

        assertProblem(response, HttpStatus.NOT_FOUND);
    }

    @Test
    void missingOrNonIntegerTtlReturnsProblemJson400() throws Exception {
        UUID eventId = UUID.randomUUID();
        initializeAndDiscoverLayout(eventId);

        assertProblem(putHoldTtl(eventId, null), HttpStatus.BAD_REQUEST);
        assertProblem(putHoldTtl(eventId, "{\"ttlSeconds\":\"abc\"}"), HttpStatus.BAD_REQUEST);
        assertProblem(putHoldTtl(eventId, "{}"), HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------------ utils

    private ResponseEntity<String> putHoldTtl(UUID eventId, String body) {
        return put("/internal/events/" + eventId + "/hold-ttl", body);
    }
}
