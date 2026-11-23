package com.ticketya.catalog.api.event;

import com.ticketya.catalog.application.event.InventoryCancellationClient;
import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.infrastructure.persistence.venue.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class EventDraftUpdatePostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired MockMvc mvc;
    @Autowired VenueRepository venues;
    @Autowired JdbcTemplate jdbc;
    @MockBean InventoryCancellationClient inventory;

    private final UUID organizer = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @BeforeEach
    void cleanDatabase() {
        for (String table : List.of("snapshot_seats", "snapshot_sectors",
                "snapshot_participants", "event_snapshots", "event_sector_offerings",
                "event_participants", "events", "venue_seats", "venue_sectors", "venues")) {
            jdbc.update("delete from " + table);
        }
    }

    @Test
    void updatingDraftWithUnchangedParticipantsDoesNotViolateUniqueConstraint() throws Exception {
        Venue venue = new Venue("Arena", "Av 1", "Buenos Aires", BigDecimal.ZERO, BigDecimal.ZERO);
        venues.saveAndFlush(venue);

        String initialBody = """
                {"name":"Show","description":"Live show","eventType":"MUSIC",
                 "startsAt":"2030-01-01T20:00:00Z","venueId":"%s",
                 "participants":["Band","Singer"]}
                """.formatted(venue.getId());
        String response = mvc.perform(post("/api/management/events")
                        .header("X-User-Id", organizer)
                        .header("X-User-Role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID eventId = UUID.fromString(
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .readTree(response).get("id").asText());
        List<UUID> participantIdsBefore = jdbc.query(
                "select id from event_participants where event_id=? order by name",
                (rs, row) -> rs.getObject("id", UUID.class), eventId);

        String updatedBody = """
                {"name":"Show","description":"Date updated","eventType":"MUSIC",
                 "startsAt":"2031-01-01T20:00:00Z","venueId":"%s",
                 "participants":["Band","Singer"]}
                """.formatted(venue.getId());
        mvc.perform(put("/api/management/events/" + eventId)
                        .header("X-User-Id", organizer)
                        .header("X-User-Role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.description").value("Date updated"))
                .andExpect(jsonPath("$.participants.length()").value(2));

        List<UUID> participantIdsAfter = jdbc.query(
                "select id from event_participants where event_id=? order by name",
                (rs, row) -> rs.getObject("id", UUID.class), eventId);
        List<String> participantNames = jdbc.query(
                "select name from event_participants where event_id=? order by name",
                (rs, row) -> rs.getString("name"), eventId);
        assertThat(participantIdsAfter).containsExactlyElementsOf(participantIdsBefore);
        assertThat(participantNames).containsExactly("Band", "Singer");
    }
}
