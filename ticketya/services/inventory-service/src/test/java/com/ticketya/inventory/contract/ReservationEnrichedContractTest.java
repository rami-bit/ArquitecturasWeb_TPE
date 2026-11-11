package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de la respuesta de reserva enriquecida de `POST /internal/reservations`,
 * `GET /internal/reservations/by-order/{orderId}` y
 * `GET /internal/reservations/{reservationId}`.
 *
 * Además de `sectorId`, `seatId` y `quantity`, cada ítem expone los datos de origen
 * y presentación que Purchase Service necesita para valorizar la orden y emitir
 * tickets sin acceder a `inventory_db`: `sourceSectorId`, `sourceSeatId`,
 * `sectorName`, `sectorType`, `rowLabel` y `seatNumber` (ver `docs/openapi.yaml`,
 * schema `ReservationItem`). La respuesta nunca incluye precios ni datos del
 * comprador.
 */
@Testcontainers(disabledWithoutDocker = true)
class ReservationEnrichedContractTest extends InventoryContractTestBase {

    /** Campos exactos que el schema `ReservationItem` permite en cada ítem. */
    private static final Set<String> CONTRACTED_ITEM_FIELDS = Set.of(
            "sectorId", "seatId", "quantity", "sourceSectorId", "sourceSeatId",
            "sectorName", "sectorType", "rowLabel", "seatNumber");

    /** Datos económicos o del comprador que jamás deben aparecer en la reserva. */
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "price", "unitPrice", "currency", "amount", "subtotal", "total",
            "customerId", "buyer", "payment", "token", "email");

    // ------------------------------------------------------- sector NUMBERED

    @Test
    void numberedItemCarriesCatalogSourceAndPresentationData() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        JsonNode catalog = catalogSnapshot(eventId);
        JsonNode platea = sectorNamed(catalog.path("sectors"), "Platea");
        assertThat(platea).as("sector Platea en el snapshot de Catalog").isNotNull();

        // Fila/número del asiento tal como lo conoce Inventory, y su origen en Catalog.
        JsonNode inventorySeat = availabilitySeat(eventId, layout.seatOneId());
        JsonNode sourceSeat = seatAtPosition(platea,
                inventorySeat.path("row").asText(), inventorySeat.path("number").asInt());

        ResponseEntity<String> created = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId())));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode items = parseBody(created).path("items");
        assertThat(items).hasSize(1);
        JsonNode item = items.get(0);

        // Claves conservadas con su significado actual.
        assertThat(requiredText(item, "sectorId"))
                .isEqualTo(layout.numberedSectorId().toString());
        assertThat(requiredText(item, "seatId"))
                .isEqualTo(layout.seatOneId().toString());
        assertThat(requiredInt(item, "quantity")).isEqualTo(1);

        // Datos de origen y presentación agregados.
        assertThat(requiredText(item, "sourceSectorId"))
                .isEqualTo(platea.path("sourceSectorId").asText());
        assertThat(requiredText(item, "sourceSeatId"))
                .isEqualTo(sourceSeat.path("sourceSeatId").asText());
        assertThat(requiredText(item, "sectorName"))
                .isEqualTo(platea.path("name").asText());
        assertThat(requiredText(item, "sectorType"))
                .isEqualTo("NUMBERED");
        assertThat(requiredText(item, "rowLabel"))
                .isEqualTo(inventorySeat.path("row").asText());
        assertThat(requiredInt(item, "seatNumber"))
                .isEqualTo(inventorySeat.path("number").asInt());

        assertThat(fieldNamesOf(item))
                .as("el ítem declara exactamente los campos del contrato")
                .containsExactlyInAnyOrderElementsOf(CONTRACTED_ITEM_FIELDS);
    }

    // ------------------------------------------------- sector GENERAL_ADMISSION

    @Test
    void generalAdmissionItemCarriesSourceDataWithNullSeatFields() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        JsonNode catalog = catalogSnapshot(eventId);
        JsonNode campo = sectorNamed(catalog.path("sectors"), "Campo");
        assertThat(campo).as("sector Campo en el snapshot de Catalog").isNotNull();

        ResponseEntity<String> created = post("/internal/reservations",
                reservationBody(UUID.randomUUID(), eventId,
                        quantityItem(layout.generalSectorId(), 2)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode items = parseBody(created).path("items");
        assertThat(items).hasSize(1);
        JsonNode item = items.get(0);

        // Claves conservadas: quantity retenida y sin asiento.
        assertThat(requiredText(item, "sectorId"))
                .isEqualTo(layout.generalSectorId().toString());
        assertNullableFieldPresent(item, "seatId");
        assertThat(requiredInt(item, "quantity")).isEqualTo(2);

        // Origen del sector; los campos de asiento existen pero son null.
        assertThat(requiredText(item, "sourceSectorId"))
                .isEqualTo(campo.path("sourceSectorId").asText());
        assertThat(requiredText(item, "sectorName"))
                .isEqualTo(campo.path("name").asText());
        assertThat(requiredText(item, "sectorType"))
                .isEqualTo("GENERAL_ADMISSION");
        assertNullableFieldPresent(item, "sourceSeatId");
        assertNullableFieldPresent(item, "rowLabel");
        assertNullableFieldPresent(item, "seatNumber");

        assertThat(fieldNamesOf(item))
                .as("el ítem declara exactamente los campos del contrato")
                .containsExactlyInAnyOrderElementsOf(CONTRACTED_ITEM_FIELDS);
    }

    // --------------------------------------------------- los tres endpoints

    @Test
    void everyReservationEndpointReturnsTheSameEnrichedItems() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID orderId = UUID.randomUUID();
        String body = reservationBody(orderId, eventId,
                seatItem(layout.numberedSectorId(), layout.seatOneId()) + ","
                        + quantityItem(layout.generalSectorId(), 2));

        ResponseEntity<String> created = post("/internal/reservations", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode reservation = parseBody(created);
        String reservationId = reservation.path("reservationId").asText();

        ResponseEntity<String> byOrder = get("/internal/reservations/by-order/" + orderId);
        assertThat(byOrder.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byOrder.getHeaders().getFirst(CORRELATION_HEADER)).isNotBlank();

        ResponseEntity<String> byId = get("/internal/reservations/" + reservationId);
        assertThat(byId.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byId.getHeaders().getFirst(CORRELATION_HEADER)).isNotBlank();

        JsonNode createdItems = reservation.path("items");
        JsonNode byOrderItems = parseBody(byOrder).path("items");
        JsonNode byIdItems = parseBody(byId).path("items");

        assertThat(createdItems).hasSize(2);
        assertThat(byOrderItems)
                .as("GET by-order devuelve los mismos ítems enriquecidos que el POST")
                .isEqualTo(createdItems);
        assertThat(byIdItems)
                .as("GET by-id devuelve los mismos ítems enriquecidos que el POST")
                .isEqualTo(createdItems);

        for (JsonNode item : createdItems) {
            assertThat(fieldNamesOf(item))
                    .as("el ítem declara exactamente los campos del contrato")
                    .containsExactlyInAnyOrderElementsOf(CONTRACTED_ITEM_FIELDS);
            assertThat(requiredText(item, "sourceSectorId")).isNotBlank();
            assertThat(requiredText(item, "sectorName")).isNotBlank();
            assertThat(requiredText(item, "sectorType")).isNotBlank();
        }
    }

    // ------------------------------------------------------------- idempotencia

    @Test
    void idempotentReplayReturnsTheSameEnrichedReservation() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        String body = reservationBody(UUID.randomUUID(), eventId,
                seatItem(layout.numberedSectorId(), layout.seatOneId()));

        ResponseEntity<String> first = post("/internal/reservations", body);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode firstReservation = parseBody(first);

        ResponseEntity<String> replay = post("/internal/reservations", body);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode replayReservation = parseBody(replay);

        assertThat(replayReservation.path("reservationId").asText())
                .isEqualTo(firstReservation.path("reservationId").asText());
        assertThat(replayReservation.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(replayReservation.path("items"))
                .as("la repetición idempotente devuelve los mismos datos enriquecidos")
                .isEqualTo(firstReservation.path("items"));

        JsonNode item = replayReservation.path("items").get(0);
        assertThat(requiredText(item, "sourceSectorId")).isNotBlank();
        assertThat(requiredText(item, "sourceSeatId")).isNotBlank();
        assertThat(requiredText(item, "sectorName")).isEqualTo("Platea");
        assertThat(requiredText(item, "sectorType")).isEqualTo("NUMBERED");
        assertThat(requiredText(item, "rowLabel")).isNotBlank();
        assertThat(requiredInt(item, "seatNumber")).isPositive();
        assertThat(fieldNamesOf(item))
                .containsExactlyInAnyOrderElementsOf(CONTRACTED_ITEM_FIELDS);
    }

    // ------------------------------------------------------- sin precios ni comprador

    @Test
    void reservationResponsesNeverExposePricesNorBuyerData() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);
        UUID orderId = UUID.randomUUID();

        ResponseEntity<String> created = post("/internal/reservations",
                reservationBody(orderId, eventId,
                        seatItem(layout.numberedSectorId(), layout.seatOneId()) + ","
                                + quantityItem(layout.generalSectorId(), 1)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String reservationId = parseBody(created).path("reservationId").asText();

        List<ResponseEntity<String>> responses = List.of(
                created,
                get("/internal/reservations/by-order/" + orderId),
                get("/internal/reservations/" + reservationId));

        for (ResponseEntity<String> response : responses) {
            assertThat(response.getStatusCode().is2xxSuccessful())
                    .as("las tres lecturas de reserva responden 2xx, fue %s",
                            response.getStatusCode())
                    .isTrue();
            List<String> fieldNames = fieldNamesOf(parseBody(response));
            assertThat(fieldNames)
                    .as("la respuesta de reserva no expone precios ni datos del comprador")
                    .doesNotContain(FORBIDDEN_FIELDS.toArray(String[]::new));
        }
    }

    // ----------------------------------------------------------- OpenAPI runtime

    @Test
    void runtimeOpenApiDocumentsTheEnrichedReservationItem() throws Exception {
        ResponseEntity<String> response = get("/v3/api-docs");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode reservationItem = JSON.readTree(response.getBody())
                .path("components").path("schemas").path("ReservationItem");
        assertThat(reservationItem.isObject())
                .as("el runtime documenta el schema ReservationItem")
                .isTrue();

        Set<String> propertyNames = new HashSet<>();
        reservationItem.path("properties").fieldNames().forEachRemaining(propertyNames::add);
        assertThat(propertyNames)
                .as("ReservationItem declara los campos enriquecidos en runtime")
                .containsExactlyInAnyOrderElementsOf(CONTRACTED_ITEM_FIELDS);
    }

    // ---------------------------------------------------------------------- helpers

    /** Snapshot publicado que el stub de Catalog devuelve para el evento. */
    private JsonNode catalogSnapshot(UUID eventId) {
        String body = new TestRestTemplate().getForObject(
                catalogStubUrl() + "/internal/events/" + eventId + "/snapshot", String.class);
        assertThat(body)
                .as("el stub de Catalog responde el snapshot del evento %s", eventId)
                .isNotNull();
        try {
            return JSON.readTree(body);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Asiento del sector numerado en la disponibilidad pública de Inventory. */
    private JsonNode availabilitySeat(UUID eventId, UUID seatId) throws Exception {
        ResponseEntity<String> availability = getAvailability(eventId);
        assertThat(availability.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode numbered = findSector(parseBody(availability).path("sectors"), "NUMBERED");
        for (JsonNode seat : numbered.path("seats")) {
            if (seatId.toString().equals(seat.path("seatId").asText(null))) {
                return seat;
            }
        }
        throw new AssertionError("asiento " + seatId + " no figura en la disponibilidad");
    }

    /** Asiento del snapshot de Catalog con la fila y el número indicados. */
    private static JsonNode seatAtPosition(JsonNode sector, String row, int number) {
        for (JsonNode seat : sector.path("seats")) {
            if (row.equals(seat.path("row").asText(null))
                    && number == seat.path("number").asInt(-1)) {
                return seat;
            }
        }
        throw new AssertionError(
                "asiento %s/%s no figura en el snapshot de Catalog".formatted(row, number));
    }

    private static String requiredText(JsonNode item, String field) {
        assertThat(item.has(field)).as("el ítem declara %s", field).isTrue();
        String value = item.path(field).asText(null);
        assertThat(value).as("%s no puede ser null ni ausente", field).isNotNull();
        return value;
    }

    private static int requiredInt(JsonNode item, String field) {
        assertThat(item.has(field) && !item.get(field).isNull())
                .as("%s debe ser un entero informado", field)
                .isTrue();
        return item.path(field).asInt();
    }

    /** El campo existe en la respuesta (contract `required`) y su valor es null. */
    private static void assertNullableFieldPresent(JsonNode item, String field) {
        assertThat(item.has(field))
                .as("el ítem declara %s aunque su valor sea null", field)
                .isTrue();
        assertThat(item.get(field).isNull()).as("%s debe ser null", field).isTrue();
    }

    private static List<String> fieldNamesOf(JsonNode node) {
        List<String> fieldNames = new ArrayList<>();
        collectFieldNames(node, fieldNames);
        return fieldNames;
    }
}
