package com.ticketya.catalog.api.event;

import com.ticketya.catalog.application.event.InventoryCancellationClient;
import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.infrastructure.notification.EventChange;
import com.ticketya.catalog.infrastructure.notification.EventNotificationPublisher;
import com.ticketya.catalog.infrastructure.persistence.venue.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PDF-F11: Catalog avisa a Notification Service cuando un evento se publica,
 * suspende, reanuda o cancela, con ciudad y participantes, y sólo si la
 * transición se confirmó.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:event-notifications;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class EventChangeNotificationIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private VenueRepository venues;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private InventoryCancellationClient inventoryCancellation;
    @MockBean
    private EventNotificationPublisher notifications;

    private final UUID organizer = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @BeforeEach
    void clean() {
        for (String table : List.of("snapshot_seats", "snapshot_sectors", "snapshot_participants", "event_snapshots",
                "event_sector_offerings", "event_participants", "events", "venue_seats", "venue_sectors", "venues")) {
            jdbc.update("delete from " + table);
        }
    }

    @Test
    void lifecycleTransitionsNotifyWithCityAndParticipants() throws Exception {
        UUID event = publishedEvent();

        ArgumentCaptor<EventChange> captor = ArgumentCaptor.forClass(EventChange.class);
        verify(notifications, times(1)).publish(captor.capture());
        EventChange published = captor.getValue();
        assertThat(published.type()).isEqualTo("EVENT_PUBLISHED");
        assertThat(published.eventId()).isEqualTo(event);
        assertThat(published.eventName()).isEqualTo("Festival F11");
        assertThat(published.city()).isEqualTo("Rosario");
        assertThat(published.participants()).containsExactlyInAnyOrder("Banda Uno", "Solista Dos");
        assertThat(published.changeId()).isNotNull();

        admin("suspend", event).andExpect(status().isOk());
        admin("resume", event).andExpect(status().isOk());
        admin("cancel", event).andExpect(status().isOk());

        ArgumentCaptor<EventChange> all = ArgumentCaptor.forClass(EventChange.class);
        verify(notifications, times(4)).publish(all.capture());
        assertThat(all.getAllValues()).extracting(EventChange::type)
                .containsExactly("EVENT_PUBLISHED", "EVENT_SUSPENDED", "EVENT_RESUMED", "EVENT_CANCELLED");
        assertThat(all.getAllValues()).extracting(EventChange::eventId).containsOnly(event);
        assertThat(all.getAllValues()).extracting(EventChange::changeId).doesNotHaveDuplicates();
    }

    @Test
    void rejectedTransitionsDoNotNotify() throws Exception {
        UUID event = publishedEvent();
        clearInvocations(notifications);

        admin("resume", event).andExpect(status().isConflict());
        mvc.perform(post("/api/management/events/" + event + "/suspend")
                        .header("X-User-Id", organizer).header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isForbidden());

        verify(notifications, never()).publish(any());
    }

    @Test
    void republishingAnAlreadyPublishedEventDoesNotNotifyAgain() throws Exception {
        UUID event = publishedEvent();
        clearInvocations(notifications);

        mvc.perform(post("/api/management/events/" + event + "/publish")
                        .header("X-User-Id", organizer).header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isOk());

        verify(notifications, never()).publish(any());
    }

    private org.springframework.test.web.servlet.ResultActions admin(String action, UUID event) throws Exception {
        return mvc.perform(post("/api/management/events/" + event + "/" + action)
                .header("X-User-Id", admin).header("X-User-Role", "ADMIN"));
    }

    private UUID publishedEvent() throws Exception {
        Venue venue = new Venue("Arena F11", "Av 1", "Rosario", BigDecimal.ZERO, BigDecimal.ZERO);
        var sector = venue.addGeneralAdmissionSector("Campo", 100);
        venues.saveAndFlush(venue);
        String body = """
                {"name":"Festival F11","description":"Notificaciones","eventType":"MUSIC",
                 "startsAt":"2030-05-01T20:00:00-03:00","venueId":"%s",
                 "participants":["Banda Uno","Solista Dos"],
                 "offerings":[{"sectorId":"%s","price":100}]}
                """.formatted(venue.getId(), sector.getId());
        String created = mvc.perform(post("/api/management/events").header("X-User-Id", organizer)
                        .header("X-User-Role", "ORGANIZER").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID event = UUID.fromString(com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(created).get("id").asText());
        verify(notifications, never()).publish(any());
        mvc.perform(post("/api/management/events/" + event + "/publish")
                        .header("X-User-Id", organizer).header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isOk());
        return event;
    }
}
