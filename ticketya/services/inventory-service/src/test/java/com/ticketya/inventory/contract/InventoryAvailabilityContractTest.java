package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de `GET /api/inventory/events/{eventId}/availability` (superficie pública).
 */
@Testcontainers(disabledWithoutDocker = true)
class InventoryAvailabilityContractTest extends InventoryContractTestBase {

    @Test
    void returnsStructuredAvailabilityForANewlyInitializedEventWithoutIdentityHeaders()
            throws Exception {
        UUID eventId = UUID.randomUUID();

        // Sin headers de identidad: la consulta es pública.
        ResponseEntity<String> response = getAvailability(eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        MediaType contentType = response.getHeaders().getContentType();
        assertThat(contentType).isNotNull();
        assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(response.getHeaders().getFirst(CORRELATION_HEADER)).isNotBlank();

        JsonNode body = parseBody(response);
        assertThat(body.path("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(body.path("generatedAt").asText(null)).isNotBlank();
        OffsetDateTime.parse(body.path("generatedAt").asText());

        JsonNode sectors = body.path("sectors");
        assertThat(sectors.isArray()).isTrue();
        assertThat(sectors).hasSizeGreaterThanOrEqualTo(2);

        JsonNode numbered = findSector(sectors, "NUMBERED");
        assertThat(numbered).isNotNull();
        assertThat(numbered.path("sectorId").asText())
                .isEqualTo(sourceSectorId(eventId, "sector:platea").toString());
        assertThat(numbered.path("name").asText()).isEqualTo("Platea");
        assertThat(numbered.path("capacity").asInt()).isEqualTo(2);
        assertThat(numbered.path("sold").asInt()).isEqualTo(0);
        assertThat(numbered.path("reserved").asInt()).isEqualTo(0);
        assertThat(numbered.path("available").asInt()).isEqualTo(2);
        JsonNode seats = numbered.path("seats");
        assertThat(seats).hasSize(2);
        assertThat(seats.get(0).path("row").asText()).isEqualTo("A");
        assertThat(seats.get(0).path("number").asInt()).isEqualTo(1);
        assertThat(seats.get(1).path("row").asText()).isEqualTo("A");
        assertThat(seats.get(1).path("number").asInt()).isEqualTo(2);
        assertThat(seats.get(0).path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(seats.get(1).path("status").asText()).isEqualTo("AVAILABLE");

        JsonNode general = findSector(sectors, "GENERAL_ADMISSION");
        assertThat(general).isNotNull();
        assertThat(general.path("sectorId").asText())
                .isEqualTo(sourceSectorId(eventId, "sector:campo").toString());
        assertThat(general.path("name").asText()).isEqualTo("Campo");
        assertThat(general.path("capacity").asInt()).isEqualTo(3);
        assertThat(general.path("sold").asInt()).isEqualTo(0);
        assertThat(general.path("reserved").asInt()).isEqualTo(0);
        assertThat(general.path("available").asInt()).isEqualTo(3);
    }

    @Test
    void propagatesSuppliedCorrelationIdToCatalogAndBackToTheClient() {
        UUID eventId = UUID.randomUUID();

        ResponseEntity<String> response =
                getAvailability(eventId, "trace-availability-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(CORRELATION_HEADER))
                .containsExactly("trace-availability-1");
        assertThat(CATALOG_REQUESTS)
                .as("el stub de Catalog recibe el correlation id de la inicialización")
                .anySatisfy(recorded -> {
                    assertThat(recorded.path())
                            .isEqualTo("/internal/events/" + eventId + "/snapshot");
                    assertThat(recorded.correlationId()).isEqualTo("trace-availability-1");
                });
    }

    @Test
    void generatesCorrelationIdWhenAbsentAndReflectsItOnErrorBodies() {
        ResponseEntity<String> notFound = get(UNKNOWN_AVAILABILITY_PATH());

        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        String generated = notFound.getHeaders().getFirst(CORRELATION_HEADER);
        assertThat(generated).isNotBlank();
        assertThat(parseBody(notFound).path("correlationId").asText()).isEqualTo(generated);
    }

    @Test
    void unknownEventReturnsProblemJsonWithCorrelationId() {
        ResponseEntity<String> response = getAvailability(UNKNOWN_EVENT);

        assertProblem(response, HttpStatus.NOT_FOUND);
    }

    @Test
    void catalogUnavailableDuringInitializationReturnsProblemJson503() {
        ResponseEntity<String> response = getAvailability(CATALOG_DOWN_EVENT);

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(parseBody(response).path("correlationId").asText()).isNotBlank();
    }

    @Test
    void availabilityNeverExposesPricesBuyersOrdersOrReservations() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        String orderId = UUID.randomUUID().toString();
        post("/internal/reservations",
                reservationBody(UUID.fromString(orderId), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));

        ResponseEntity<String> response = getAvailability(eventId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<String> fieldNames = new ArrayList<>();
        collectFieldNames(parseBody(response), fieldNames);
        assertThat(fieldNames)
                .as("la disponibilidad pública no expone datos sensibles")
                .doesNotContain("price", "orderId", "reservationId", "buyer", "customer");
    }

    @Test
    void reflectsHeldSeatAndReservedGeneralAfterReservations() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        ResponseEntity<String> seatReservation = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));
        assertThat(seatReservation.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> generalReservation = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        quantityItem(layout.generalSectorId(), 1)));
        assertThat(generalReservation.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode sectors = parseBody(getAvailability(eventId)).path("sectors");

        JsonNode numbered = findSector(sectors, "NUMBERED");
        assertThat(numbered.path("reserved").asInt()).isEqualTo(1);
        assertThat(numbered.path("available").asInt()).isEqualTo(1);
        assertThat(seatOf(numbered, layout.seatOneId()).path("status").asText())
                .isEqualTo("HELD");
        assertThat(seatOf(numbered, layout.seatTwoId()).path("status").asText())
                .isEqualTo("AVAILABLE");

        JsonNode general = findSector(sectors, "GENERAL_ADMISSION");
        assertThat(general.path("sold").asInt()).isEqualTo(0);
        assertThat(general.path("reserved").asInt()).isEqualTo(1);
        assertThat(general.path("available").asInt()).isEqualTo(2);
    }

    @Test
    void lazilyExpiresReservationWhenAvailabilityIsRead() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        ResponseEntity<String> reservation = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId()) + ","
                                + quantityItem(layout.generalSectorId(), 1)));
        assertThat(reservation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID reservationId =
                UUID.fromString(parseBody(reservation).path("reservationId").asText());

        forceReservationExpiry(reservationId);

        JsonNode sectors = parseBody(getAvailability(eventId)).path("sectors");
        JsonNode numbered = findSector(sectors, "NUMBERED");
        assertThat(seatOf(numbered, layout.seatOneId()).path("status").asText())
                .isEqualTo("AVAILABLE");
        assertThat(numbered.path("reserved").asInt()).isEqualTo(0);
        assertThat(numbered.path("available").asInt()).isEqualTo(2);

        JsonNode general = findSector(sectors, "GENERAL_ADMISSION");
        assertThat(general.path("reserved").asInt()).isEqualTo(0);
        assertThat(general.path("available").asInt()).isEqualTo(3);
    }

    // ------------------------------------------------------------------------ utils

    private static String UNKNOWN_AVAILABILITY_PATH() {
        return "/api/inventory/events/" + UNKNOWN_EVENT + "/availability";
    }

    private static UUID sourceSectorId(UUID eventId, String label) {
        return UUID.nameUUIDFromBytes(
                (label + "|" + eventId).getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode seatOf(JsonNode numberedSector, UUID seatId) {
        for (JsonNode seat : numberedSector.path("seats")) {
            if (seatId.toString().equals(seat.path("seatId").asText(null))) {
                return seat;
            }
        }
        throw new AssertionError("asiento " + seatId + " no encontrado en la disponibilidad");
    }
}
