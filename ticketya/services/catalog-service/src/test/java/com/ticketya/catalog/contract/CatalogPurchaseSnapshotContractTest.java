package com.ticketya.catalog.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato interno `GET /internal/events/{eventId}/purchase-snapshot`: el snapshot
 * publicado que Purchase Service consume con el precio unitario de cada sector.
 *
 * Verifica que el nuevo contrato es aditivo respecto de
 * `GET /internal/events/{eventId}/snapshot` (que sigue sin precios para Inventory),
 * que sólo responde para eventos publicados y que la ruta queda documentada en el
 * OpenAPI runtime de Catalog.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:purchase-snapshot-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="})
@AutoConfigureMockMvc
class CatalogPurchaseSnapshotContractTest {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String PURCHASE_SNAPSHOT_PATH =
            "/internal/events/{eventId}/purchase-snapshot";
    private static final String INVENTORY_SNAPSHOT_PATH =
            "/internal/events/{eventId}/snapshot";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private UUID publishedEventId;
    private UUID draftEventId;
    private UUID cancelledEventId;
    private UUID snapshotId;
    private UUID sourceNumberedSectorId;
    private UUID sourceGeneralSectorId;
    private UUID sourceSeatOneId;
    private UUID sourceSeatTwoId;

    @BeforeEach
    void seedPublishedDraftAndCancelledEvents() {
        jdbc.update("delete from snapshot_seats");
        jdbc.update("delete from snapshot_sectors");
        jdbc.update("delete from snapshot_participants");
        jdbc.update("delete from event_snapshots");
        jdbc.update("delete from event_sector_offerings");
        jdbc.update("delete from event_participants");
        jdbc.update("delete from events");
        jdbc.update("delete from venue_seats");
        jdbc.update("delete from venue_sectors");
        jdbc.update("delete from venues");

        UUID venueId = UUID.randomUUID();
        UUID venueNumberedSectorId = UUID.randomUUID();
        UUID venueGeneralSectorId = UUID.randomUUID();
        UUID numberedSnapshotSectorId = UUID.randomUUID();
        UUID generalSnapshotSectorId = UUID.randomUUID();
        UUID cancelledSnapshotSectorId = UUID.randomUUID();
        publishedEventId = UUID.randomUUID();
        draftEventId = UUID.randomUUID();
        cancelledEventId = UUID.randomUUID();
        snapshotId = UUID.randomUUID();
        UUID cancelledSnapshotId = UUID.randomUUID();
        sourceNumberedSectorId = UUID.randomUUID();
        sourceGeneralSectorId = UUID.randomUUID();
        sourceSeatOneId = UUID.randomUUID();
        sourceSeatTwoId = UUID.randomUUID();
        UUID sourceCancelledSectorId = UUID.randomUUID();
        UUID organizer = UUID.fromString("10000000-0000-0000-0000-000000000001");
        OffsetDateTime startsAt = OffsetDateTime.parse("2030-06-01T21:00:00Z");

        // Plantilla del venue (los snapshots son inmutables frente a ella).
        jdbc.update("insert into venues(id,name,address,city,latitude,longitude) values(?,?,?,?,?,?)",
                venueId, "Estadio de contrato", "Av. Contrato 100", "Buenos Aires",
                BigDecimal.valueOf(-34.6037), BigDecimal.valueOf(-58.3816));
        jdbc.update("insert into venue_sectors(id,venue_id,name,sector_type,general_capacity) values(?,?,?,?,?)",
                venueNumberedSectorId, venueId, "Platea", "NUMBERED", null);
        jdbc.update("insert into venue_sectors(id,venue_id,name,sector_type,general_capacity) values(?,?,?,?,?)",
                venueGeneralSectorId, venueId, "Campo", "GENERAL_ADMISSION", 3);
        jdbc.update("insert into venue_seats(id,venue_sector_id,row_label,seat_number) values(?,?,?,?)",
                UUID.randomUUID(), venueNumberedSectorId, "A", 1);
        jdbc.update("insert into venue_seats(id,venue_sector_id,row_label,seat_number) values(?,?,?,?)",
                UUID.randomUUID(), venueNumberedSectorId, "A", 2);

        // Evento publicado con snapshot publicado (los tres sectores con precio).
        jdbc.update("insert into events(id,name,description,event_type,starts_at,organizer_id,venue_id,status) values(?,?,?,?,?,?,?,?)",
                publishedEventId, "Evento de contrato", "Snapshot para la suite de contrato",
                "CONCERT", startsAt, organizer, venueId, "PUBLISHED");
        jdbc.update("insert into event_snapshots(id,event_id,name,description,event_type,starts_at,venue_name,venue_address,venue_city,venue_latitude,venue_longitude) values(?,?,?,?,?,?,?,?,?,?,?)",
                snapshotId, publishedEventId, "Evento de contrato",
                "Snapshot para la suite de contrato", "CONCERT", startsAt,
                "Estadio de contrato", "Av. Contrato 100", "Buenos Aires",
                BigDecimal.valueOf(-34.6037), BigDecimal.valueOf(-58.3816));
        jdbc.update("insert into snapshot_sectors(id,snapshot_id,source_sector_id,name,sector_type,capacity,price) values(?,?,?,?,?,?,?)",
                numberedSnapshotSectorId, snapshotId, sourceNumberedSectorId, "Platea",
                "NUMBERED", 2, BigDecimal.valueOf(100));
        jdbc.update("insert into snapshot_seats(id,sector_id,source_seat_id,row_label,seat_number) values(?,?,?,?,?)",
                UUID.randomUUID(), numberedSnapshotSectorId, sourceSeatOneId, "A", 1);
        jdbc.update("insert into snapshot_seats(id,sector_id,source_seat_id,row_label,seat_number) values(?,?,?,?,?)",
                UUID.randomUUID(), numberedSnapshotSectorId, sourceSeatTwoId, "A", 2);
        jdbc.update("insert into snapshot_sectors(id,snapshot_id,source_sector_id,name,sector_type,capacity,price) values(?,?,?,?,?,?,?)",
                generalSnapshotSectorId, snapshotId, sourceGeneralSectorId, "Campo",
                "GENERAL_ADMISSION", 3, BigDecimal.valueOf(50));

        // Evento en borrador: nunca tuvo snapshot publicado.
        jdbc.update("insert into events(id,name,description,event_type,starts_at,organizer_id,venue_id,status) values(?,?,?,?,?,?,?,?)",
                draftEventId, "Evento en borrador", "Sin snapshot publicado",
                "CONCERT", startsAt, organizer, venueId, "DRAFT");

        // Evento cancelado con snapshot histórico: el contrato exige 404 igualmente.
        jdbc.update("insert into events(id,name,description,event_type,starts_at,organizer_id,venue_id,status) values(?,?,?,?,?,?,?,?)",
                cancelledEventId, "Evento cancelado", "Publicado y luego cancelado",
                "CONCERT", startsAt, organizer, venueId, "CANCELLED");
        jdbc.update("insert into event_snapshots(id,event_id,name,description,event_type,starts_at,venue_name,venue_address,venue_city,venue_latitude,venue_longitude) values(?,?,?,?,?,?,?,?,?,?,?)",
                cancelledSnapshotId, cancelledEventId, "Evento cancelado",
                "Publicado y luego cancelado", "CONCERT", startsAt,
                "Estadio de contrato", "Av. Contrato 100", "Buenos Aires",
                BigDecimal.valueOf(-34.6037), BigDecimal.valueOf(-58.3816));
        jdbc.update("insert into snapshot_sectors(id,snapshot_id,source_sector_id,name,sector_type,capacity,price) values(?,?,?,?,?,?,?)",
                cancelledSnapshotSectorId, cancelledSnapshotId, sourceCancelledSectorId,
                "Campo", "GENERAL_ADMISSION", 3, BigDecimal.valueOf(75));
    }

    // ------------------------------------------------------------------ 200 + precios

    @Test
    void publishedEventReturnsThePurchaseSnapshotWithSectorPrices() throws Exception {
        String body = performPurchaseSnapshotGet(publishedEventId, "trace-purchase-snapshot-1")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CORRELATION_HEADER, "trace-purchase-snapshot-1"))
                .andReturn().getResponse().getContentAsString();

        JsonNode snapshot = JSON.readTree(body);
        assertThat(snapshot.path("eventId").asText()).isEqualTo(publishedEventId.toString());
        assertThat(snapshot.path("snapshotId").asText()).isEqualTo(snapshotId.toString());
        assertThat(snapshot.path("name").asText()).isEqualTo("Evento de contrato");
        assertThat(snapshot.path("description").asText())
                .isEqualTo("Snapshot para la suite de contrato");
        assertThat(snapshot.path("eventType").asText()).isEqualTo("CONCERT");

        JsonNode venue = snapshot.path("venue");
        assertThat(venue.path("name").asText()).isEqualTo("Estadio de contrato");
        assertThat(venue.path("address").asText()).isEqualTo("Av. Contrato 100");
        assertThat(venue.path("city").asText()).isEqualTo("Buenos Aires");
        assertThat(venue.path("latitude").decimalValue())
                .isEqualByComparingTo("-34.6037");
        assertThat(venue.path("longitude").decimalValue())
                .isEqualByComparingTo("-58.3816");

        JsonNode sectors = snapshot.path("sectors");
        assertThat(sectors.isArray()).isTrue();
        assertThat(sectors).hasSize(2);
        for (JsonNode sector : sectors) {
            assertThat(sector.has("price"))
                    .as("el sector %s declara su precio unitario",
                            sector.path("name").asText())
                    .isTrue();
            assertThat(sector.path("price").decimalValue().signum())
                    .as("el precio del sector %s es positivo", sector.path("name").asText())
                    .isPositive();
        }

        JsonNode numbered = sectorOfType(sectors, "NUMBERED");
        assertThat(numbered.path("sourceSectorId").asText())
                .isEqualTo(sourceNumberedSectorId.toString());
        assertThat(numbered.path("name").asText()).isEqualTo("Platea");
        assertThat(numbered.path("capacity").asInt()).isEqualTo(2);
        assertThat(numbered.path("price").decimalValue())
                .isEqualByComparingTo(BigDecimal.valueOf(100));
        JsonNode seats = numbered.path("seats");
        assertThat(seats).hasSize(2);
        assertThat(seats.get(0).path("sourceSeatId").asText())
                .isEqualTo(sourceSeatOneId.toString());
        assertThat(seats.get(0).path("row").asText()).isEqualTo("A");
        assertThat(seats.get(0).path("number").asInt()).isEqualTo(1);
        assertThat(seats.get(1).path("sourceSeatId").asText())
                .isEqualTo(sourceSeatTwoId.toString());
        assertThat(seats.get(1).path("row").asText()).isEqualTo("A");
        assertThat(seats.get(1).path("number").asInt()).isEqualTo(2);

        JsonNode general = sectorOfType(sectors, "GENERAL_ADMISSION");
        assertThat(general.path("sourceSectorId").asText())
                .isEqualTo(sourceGeneralSectorId.toString());
        assertThat(general.path("name").asText()).isEqualTo("Campo");
        assertThat(general.path("capacity").asInt()).isEqualTo(3);
        assertThat(general.path("price").decimalValue())
                .isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(general.path("seats").isArray()).isTrue();
        assertThat(general.path("seats")).isEmpty();
    }

    @Test
    void purchaseSnapshotIsTheInventorySnapshotPlusTheSectorPrices() throws Exception {
        JsonNode purchase = JSON.readTree(
                performPurchaseSnapshotGet(publishedEventId, "trace-purchase-strip")
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        JsonNode inventory = JSON.readTree(
                performSnapshotGet(publishedEventId, "trace-inventory-strip")
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        JsonNode purchaseWithoutPrices = purchase.deepCopy();
        assertThat(purchaseWithoutPrices.path("sectors"))
                .hasSize(inventory.path("sectors").size());
        for (JsonNode sector : purchaseWithoutPrices.path("sectors")) {
            ((ObjectNode) sector).remove("price");
        }

        assertThat(purchaseWithoutPrices)
                .as("el purchase-snapshot es idéntico al snapshot de inventario "
                        + "salvo por el precio de cada sector")
                .isEqualTo(inventory);
    }

    // ------------------------------------------------------------------------ 404

    @Test
    void draftEventWithoutSnapshotReturnsProblemJson404() throws Exception {
        String body = performPurchaseSnapshotGet(draftEventId, "trace-purchase-draft")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CORRELATION_HEADER, "trace-purchase-draft"))
                .andReturn().getResponse().getContentAsString();

        JsonNode problem = JSON.readTree(body);
        assertThat(problem.path("status").asInt()).isEqualTo(404);
        assertThat(problem.path("correlationId").asText()).isEqualTo("trace-purchase-draft");
    }

    @Test
    void cancelledEventWithPublishedSnapshotReturnsProblemJson404() throws Exception {
        String body = performPurchaseSnapshotGet(cancelledEventId, "trace-purchase-cancelled")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CORRELATION_HEADER, "trace-purchase-cancelled"))
                .andReturn().getResponse().getContentAsString();

        JsonNode problem = JSON.readTree(body);
        assertThat(problem.path("status").asInt()).isEqualTo(404);
        assertThat(problem.path("correlationId").asText())
                .isEqualTo("trace-purchase-cancelled");
    }

    @Test
    void unknownEventReturnsProblemJson404() throws Exception {
        performPurchaseSnapshotGet(UUID.randomUUID(), "trace-purchase-unknown")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(CORRELATION_HEADER, "trace-purchase-unknown"));
    }

    // ------------------------------------------------------------------ no regresión

    @Test
    void inventorySnapshotKeepsExcludingPrices() throws Exception {
        String body = performSnapshotGet(publishedEventId, "trace-inventory-no-price")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(CORRELATION_HEADER, "trace-inventory-no-price"))
                .andReturn().getResponse().getContentAsString();

        JsonNode snapshot = JSON.readTree(body);
        assertThat(snapshot.path("sectors")).hasSize(2);
        List<String> fieldNames = new ArrayList<>();
        collectFieldNames(snapshot, fieldNames);
        assertThat(fieldNames)
                .as("el snapshot que consume Inventory sigue sin precios")
                .doesNotContain("price", "minimumPrice", "offerings", "currency");
    }

    // ------------------------------------------------------------------- documentación

    @Test
    void purchaseSnapshotIsDocumentedInTheRuntimeOpenApiDocument() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode paths = JSON.readTree(body).path("paths");
        JsonNode operation = paths.path(PURCHASE_SNAPSHOT_PATH).path("get");
        assertThat(operation.isObject())
                .as("GET %s debe documentarse en /v3/api-docs", PURCHASE_SNAPSHOT_PATH)
                .isTrue();
        assertThat(operation.path("operationId").asText())
                .isEqualTo("getCatalogPurchaseSnapshot");
        assertThat(operation.path("responses").has("200"))
                .as("documenta la respuesta 200").isTrue();
        assertThat(operation.path("responses").has("404"))
                .as("documenta la respuesta 404").isTrue();

        // No regresión: el snapshot de inventario sigue documentado en runtime.
        assertThat(paths.path(INVENTORY_SNAPSHOT_PATH).path("get").isObject())
                .as("GET %s sigue documentado", INVENTORY_SNAPSHOT_PATH)
                .isTrue();
    }

    @Test
    void purchaseSnapshotPropagatesTheSuppliedCorrelationId() throws Exception {
        performPurchaseSnapshotGet(publishedEventId, "trace-purchase-echo")
                .andExpect(header().string(CORRELATION_HEADER, "trace-purchase-echo"));
    }

    // ------------------------------------------------------------------------ utils

    private ResultActions performPurchaseSnapshotGet(UUID eventId, String correlationId)
            throws Exception {
        return mvc.perform(get(PURCHASE_SNAPSHOT_PATH, eventId)
                .header(CORRELATION_HEADER, correlationId));
    }

    private ResultActions performSnapshotGet(UUID eventId, String correlationId)
            throws Exception {
        return mvc.perform(get(INVENTORY_SNAPSHOT_PATH, eventId)
                .header(CORRELATION_HEADER, correlationId));
    }

    private static JsonNode sectorOfType(JsonNode sectors, String type) {
        for (JsonNode sector : sectors) {
            if (type.equals(sector.path("type").asText(null))) {
                return sector;
            }
        }
        throw new AssertionError("sector de tipo " + type + " no encontrado");
    }

    private static void collectFieldNames(JsonNode node, List<String> sink) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(sink::add);
            node.forEach(child -> collectFieldNames(child, sink));
        } else if (node.isArray()) {
            node.forEach(child -> collectFieldNames(child, sink));
        }
    }
}
