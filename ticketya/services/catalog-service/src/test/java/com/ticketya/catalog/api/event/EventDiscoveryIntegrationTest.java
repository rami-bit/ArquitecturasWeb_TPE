package com.ticketya.catalog.api.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:event-discovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class EventDiscoveryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final UUID organizer = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @BeforeEach void clear() {
        jdbc.update("delete from snapshot_seats"); jdbc.update("delete from snapshot_sectors");
        jdbc.update("delete from snapshot_participants"); jdbc.update("delete from event_snapshots");
        jdbc.update("delete from event_sector_offerings"); jdbc.update("delete from event_participants");
        jdbc.update("delete from events"); jdbc.update("delete from venue_seats");
        jdbc.update("delete from venue_sectors"); jdbc.update("delete from venues");
        seed("20000000-0000-0000-0000-000000000001", "Festival Alpha", "MUSIC", "Alpha outdoor festival", "2030-06-10T20:00:00-03:00", "Buenos Aires", "Band Alpha", 20.00, "PUBLISHED");
        seed("20000000-0000-0000-0000-000000000002", "Alpha Comedy", "COMEDY", "An evening of humor", "2030-06-11T20:00:00-03:00", "Buenos Aires", "Comic Alpha", 10.00, "PUBLISHED");
        seed("20000000-0000-0000-0000-000000000003", "Hidden Alpha", "MUSIC", "Unpublished show", "2030-06-10T20:00:00-03:00", "Buenos Aires", "Band Alpha", 5.00, "DRAFT");
        seed("20000000-0000-0000-0000-000000000004", "Suspended Alpha", "MUSIC", "Paused show", "2030-06-10T20:00:00-03:00", "Buenos Aires", "Band Alpha", 3.00, "SUSPENDED");
    }

    @Test void filtersPublishedSnapshotsPaginatesAndSortsWithStableTieBreaks() throws Exception {
        mvc.perform(get("/api/events").param("type", "MUSIC").param("participant", "BAND").param("city", "Buenos Aires")
                        .param("organizerId", organizer.toString()).param("from", "2030-06-10T20:00:00-03:00").param("to", "2030-06-10T20:00:00-03:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].name").value("Festival Alpha"));
        mvc.perform(get("/api/events").param("sort", "price").param("direction", "asc").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Alpha Comedy")).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/events").param("sort", "date").param("direction", "desc").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Festival Alpha")).andExpect(jsonPath("$.page").value(1));
        mvc.perform(get("/api/events").param("sort", "relevance").param("q", "alpha"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Alpha Comedy"));
        assertThat(jdbc.queryForObject("select count(*) from information_schema.indexes where index_name = 'IX_EVENTS_STATUS_STARTS_AT_ID'", Integer.class)).isEqualTo(1);
    }

    @Test void validatesSearchAndReturnsOnlyPublicSnapshotDetailsWithoutIdentity() throws Exception {
        mvc.perform(get("/api/events").param("from", "2030-06-12T00:00:00Z").param("to", "2030-06-11T00:00:00Z"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/events").param("size", "51"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/events").param("sort", "relevance"))
                .andExpect(status().isBadRequest());
        String sourceSectorId=jdbc.queryForObject("select ss.source_sector_id from snapshot_sectors ss join event_snapshots s on s.id=ss.snapshot_id where s.event_id=?",UUID.class,UUID.fromString("20000000-0000-0000-0000-000000000001")).toString();
        mvc.perform(get("/api/events/{id}", "20000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Alpha outdoor festival"))
                .andExpect(jsonPath("$.participants[0]").value("Band Alpha"))
                .andExpect(jsonPath("$.sectors[0].id").value(sourceSectorId)).andExpect(jsonPath("$.sectors[0].price").value(20.00)).andExpect(jsonPath("$.availability").doesNotExist());
        mvc.perform(get("/api/events/{id}", "20000000-0000-0000-0000-000000000003")).andExpect(status().isNotFound());
        mvc.perform(get("/api/events/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        String api = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(api).contains("/api/events", "organizerId", "participant", "relevance", "totalElements");
    }

    private void seed(String id, String name, String type, String description, String startsAt, String city, String participant, double price, String status) {
        UUID event = UUID.fromString(id), snapshot = UUID.randomUUID(), sector = UUID.randomUUID();
        UUID venue = UUID.randomUUID();
        jdbc.update("insert into venues(id,name,address,city,latitude,longitude) values(?,?,?,?,?,?)", venue, "Venue", "Street 1", city, BigDecimal.ZERO, BigDecimal.ZERO);
        jdbc.update("insert into events(id,name,description,event_type,starts_at,organizer_id,venue_id,status) values(?,?,?,?,?,?,?,?)", event, name, description, type, OffsetDateTime.parse(startsAt), organizer, venue, status);
        jdbc.update("insert into event_snapshots(id,event_id,name,description,event_type,starts_at,venue_name,venue_address,venue_city,venue_latitude,venue_longitude) values(?,?,?,?,?,?,?,?,?,?,?)", snapshot, event, name, description, type, OffsetDateTime.parse(startsAt), "Venue", "Street 1", city, BigDecimal.ZERO, BigDecimal.ZERO);
        jdbc.update("insert into snapshot_participants(id,snapshot_id,name) values(?,?,?)", UUID.randomUUID(), snapshot, participant);
        jdbc.update("insert into snapshot_sectors(id,snapshot_id,source_sector_id,name,sector_type,capacity,price) values(?,?,?,?,?,?,?)", sector, snapshot, UUID.randomUUID(), "General", "GENERAL_ADMISSION", 100, BigDecimal.valueOf(price));
    }
}
