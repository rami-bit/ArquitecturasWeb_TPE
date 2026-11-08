package com.ticketya.catalog.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

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
 * Contrato interno `GET /internal/events/{eventId}/snapshot`: el snapshot publicado que
 * Inventory consume para materializar el layout, con identificadores de origen, sin
 * precios e inmutable frente a cambios de la plantilla del venue.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:snapshot-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="})
@AutoConfigureMockMvc
class CatalogSnapshotContractTest {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private UUID venueId;
    private UUID numberedSectorId;
    private UUID generalSectorId;
    private UUID publishedEventId;
    private UUID snapshotId;
    private UUID sourceNumberedSectorId;
    private UUID sourceGeneralSectorId;
    private UUID sourceSeatOneId;
    private UUID sourceSeatTwoId;
    private UUID draftEventId;

    @BeforeEach
    void seedPublishedEventWithSnapshot() {
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

        venueId = UUID.randomUUID();
        numberedSectorId = UUID.randomUUID();
        generalSectorId = UUID.randomUUID();
        publishedEventId = UUID.randomUUID();
        snapshotId = UUID.randomUUID();
        sourceNumberedSectorId = UUID.randomUUID();
        sourceGeneralSectorId = UUID.randomUUID();
        sourceSeatOneId = UUID.randomUUID();
        sourceSeatTwoId = UUID.randomUUID();
        draftEventId = UUID.randomUUID();
        UUID organizer = UUID.fromString("10000000-0000-0000-0000-000000000001");
        OffsetDateTime startsAt = OffsetDateTime.parse("2030-06-01T21:00:00Z");

        jdbc.update("insert into venues(id,name,address,city,latitude,longitude) values(?,?,?,?,?,?)",
                venueId, "Estadio de contrato", "Av. Contrato 100", "Buenos Aires",
                BigDecimal.valueOf(-34.6037), BigDecimal.valueOf(-58.3816));
        jdbc.update("insert into venue_sectors(id,venue_id,name,sector_type,general_capacity) values(?,?,?,?,?)",
                numberedSectorId, venueId, "Platea", "NUMBERED", null);
        jdbc.update("insert into venue_sectors(id,venue_id,name,sector_type,general_capacity) values(?,?,?,?,?)",
                generalSectorId, venueId, "Campo", "GENERAL_ADMISSION", 3);
        jdbc.update("insert into venue_seats(id,venue_sector_id,row_label,seat_number) values(?,?,?,?)",
                UUID.randomUUID(), numberedSectorId, "A", 1);
        jdbc.update("insert into venue_seats(id,venue_sector_id,row_label,seat_number) values(?,?,?,?)",
                UUID.randomUUID(), numberedSectorId, "A", 2);

        jdbc.update("insert into events(id,name,description,event_type,starts_at,organizer_id,venue_id,status) values(?,?,?,?,?,?,?,?)",
                publishedEventId, "Evento de contrato", "Snapshot para la suite de contrato",
                "CONCERT", startsAt, organizer, venueId, "PUBLISHED");
        jdbc.update("insert into events(id,name,description,event_type,starts_at,organizer_id,venue_id,status) values(?,?,?,?,?,?,?,?)",
                draftEventId, "Evento en borrador", "Sin snapshot publicado",
                "CONCERT", startsAt, organizer, venueId, "DRAFT");

        jdbc.update("insert into event_snapshots(id,event_id,name,description,event_type,starts_at,venue_name,venue_address,venue_city,venue_latitude,venue_longitude) values(?,?,?,?,?,?,?,?,?,?,?)",
                snapshotId, publishedEventId, "Evento de contrato",
                "Snapshot para la suite de contrato", "CONCERT", startsAt,
                "Estadio de contrato", "Av. Contrato 100", "Buenos Aires",
                BigDecimal.valueOf(-34.6037), BigDecimal.valueOf(-58.3816));
        jdbc.update("insert into snapshot_sectors(id,snapshot_id,source_sector_id,name,sector_type,capacity,price) values(?,?,?,?,?,?,?)",
                numberedSectorId, snapshotId, sourceNumberedSectorId, "Platea",
                "NUMBERED", 2, BigDecimal.valueOf(100));
        jdbc.update("insert into snapshot_seats(id,sector_id,source_seat_id,row_label,seat_number) values(?,?,?,?,?)",
                UUID.randomUUID(), numberedSectorId, sourceSeatOneId, "A", 1);
        jdbc.update("insert into snapshot_seats(id,sector_id,source_seat_id,row_label,seat_number) values(?,?,?,?,?)",
                UUID.randomUUID(), numberedSectorId, sourceSeatTwoId, "A", 2);
        jdbc.update("insert into snapshot_sectors(id,snapshot_id,source_sector_id,name,sector_type,capacity,price) values(?,?,?,?,?,?,?)",
                generalSectorId, snapshotId, sourceGeneralSectorId, "Campo",
                "GENERAL_ADMISSION", 3, BigDecimal.valueOf(50));
    }

    @Test
    void returnsPublishedSnapshotWithOriginIdentifiersVenueAndSectors() throws Exception {
        String body = performSnapshotGet(publishedEventId)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().exists(CORRELATION_HEADER))
                .andReturn().getResponse().getContentAsString();

        JsonNode snapshot = JSON.readTree(body);
        assertThat(snapshot.path("eventId").asText()).isEqualTo(publishedEventId.toString());
        UUID snapshotUuid = UUID.fromString(snapshot.path("snapshotId").asText());
        assertThat(snapshotUuid).isNotNull();
        assertThat(snapshot.path("name").asText()).isEqualTo("Evento de contrato");
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

        JsonNode numbered = sectorOfType(sectors, "NUMBERED");
        assertThat(numbered.path("sourceSectorId").asText())
                .isEqualTo(sourceNumberedSectorId.toString());
        assertThat(numbered.path("name").asText()).isEqualTo("Platea");
        assertThat(numbered.path("capacity").asInt()).isEqualTo(2);
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
        assertThat(general.path("seats").isArray()).isTrue();
        assertThat(general.path("seats")).isEmpty();
    }

    @Test
    void snapshotNeverExposesPrice() throws Exception {
        String body = performSnapshotGet(publishedEventId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> fieldNames = new ArrayList<>();
        collectFieldNames(JSON.readTree(body), fieldNames);
        assertThat(fieldNames)
                .as("el contrato interno no incluye precios ni ofertas económicas")
                .doesNotContain("price", "minimumPrice", "offerings");
    }

    @Test
    void unknownEventReturnsProblemJson404() throws Exception {
        performSnapshotGet(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists(CORRELATION_HEADER));
    }

    @Test
    void draftEventWithoutSnapshotReturnsProblemJson404() throws Exception {
        performSnapshotGet(draftEventId)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void snapshotIsImmutableWhenTheVenueTemplateChangesAfterPublishing() throws Exception {
        String before = performSnapshotGet(publishedEventId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Cambia la plantilla canónica del venue después de publicar.
        jdbc.update("update venues set name = ?, address = ?, city = ? where id = ?",
                "Venue renovado", "Av. Nueva 999", "Córdoba", venueId);
        jdbc.update("update venue_sectors set name = ? where id = ?",
                "Platea renovada", numberedSectorId);
        jdbc.update("insert into venue_seats(id,venue_sector_id,row_label,seat_number) values(?,?,?,?)",
                UUID.randomUUID(), numberedSectorId, "B", 1);

        String after = performSnapshotGet(publishedEventId)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(after)
                .as("el snapshot publicado no se reescribe")
                .isEqualTo(before);

        JsonNode snapshot = JSON.readTree(after);
        assertThat(snapshot.path("snapshotId").asText()).isEqualTo(snapshotId.toString());
        JsonNode numbered = sectorOfType(snapshot.path("sectors"), "NUMBERED");
        assertThat(numbered.path("capacity").asInt()).isEqualTo(2);
        assertThat(numbered.path("seats")).hasSize(2);
        assertThat(numbered.path("seats").get(0).path("sourceSeatId").asText())
                .isEqualTo(sourceSeatOneId.toString());
        assertThat(numbered.path("seats").get(1).path("sourceSeatId").asText())
                .isEqualTo(sourceSeatTwoId.toString());
    }

    // ------------------------------------------------------------------------ utils

    private org.springframework.test.web.servlet.ResultActions performSnapshotGet(UUID eventId)
            throws Exception {
        return mvc.perform(get("/internal/events/{eventId}/snapshot", eventId)
                .header(CORRELATION_HEADER, "trace-snapshot-1"));
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
