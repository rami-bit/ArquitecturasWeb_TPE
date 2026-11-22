package com.ticketya.catalog.api.event;

import com.ticketya.catalog.application.event.InventoryCancellationClient;
import com.ticketya.catalog.domain.event.Event;
import com.ticketya.catalog.domain.event.EventSnapshot;
import com.ticketya.catalog.domain.event.EventStatus;
import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.infrastructure.cache.EventDiscoveryCache;
import com.ticketya.catalog.infrastructure.notification.EventChange;
import com.ticketya.catalog.infrastructure.notification.EventNotificationPublisher;
import com.ticketya.catalog.infrastructure.persistence.event.EventRepository;
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
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class EventPostponementPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired MockMvc mvc;
    @Autowired VenueRepository venues;
    @Autowired EventRepository events;
    @Autowired JdbcTemplate jdbc;
    @Autowired EventDiscoveryCache cache;
    @MockBean EventNotificationPublisher notifications;
    @MockBean InventoryCancellationClient inventory;
    @MockBean Clock clock;

    private final UUID owner = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final Instant now = Instant.parse("2026-11-22T00:00:00Z");

    @BeforeEach void clean() {
        when(clock.instant()).thenReturn(now);
        jdbc.update("delete from event_schedule_changes");
        for (String table : new String[]{"snapshot_seats","snapshot_sectors","snapshot_participants","event_snapshots","event_sector_offerings","event_participants","events","venue_seats","venue_sectors","venues"}) jdbc.update("delete from " + table);
        clearInvocations(notifications);
    }

    @Test void postponesOncePreservesSnapshotUpdatesDiscoveryAndNotifiesAfterCommit() throws Exception {
        UUID id = published("2030-12-01T20:00:00Z");
        mvc.perform(get("/api/events/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.startsAt").value("2030-12-01T20:00:00Z"));
        mvc.perform(get("/api/events").param("from", "2030-12-01T00:00:00Z").param("to", "2030-12-02T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        long invalidations = cache.invalidations();
        String body = "{\"startsAt\":\"2030-12-03T20:00:00Z\",\"expectedScheduleRevision\":0}";
        String first = mvc.perform(patch("/api/management/events/" + id + "/schedule").header("X-User-Id", owner).header("X-User-Role", "ORGANIZER").header("Idempotency-Key", "postpone-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scheduleRevision").value(1)).andExpect(jsonPath("$.previousStartsAt").value("2030-12-01T20:00:00Z")).andExpect(jsonPath("$.startsAt").value("2030-12-03T20:00:00Z")).andReturn().getResponse().getContentAsString();
        String replay = mvc.perform(patch("/api/management/events/" + id + "/schedule").header("X-User-Id", owner).header("X-User-Role", "ORGANIZER").header("Idempotency-Key", "postpone-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(replay).isEqualTo(first);
        assertThat(cache.invalidations()).isEqualTo(invalidations + 1);
        assertThat(jdbc.queryForObject("select count(*) from event_schedule_changes where event_id=?", Integer.class, id)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("insert into event_schedule_changes(change_id,event_id,idempotency_key,actor_id,expected_schedule_revision,previous_starts_at,starts_at,schedule_revision) values (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), id, "postpone-1", owner, 0,
                OffsetDateTime.parse("2030-12-01T20:00:00Z"), OffsetDateTime.parse("2030-12-04T20:00:00Z"), 2))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select starts_at from event_snapshots where event_id=?", OffsetDateTime.class, id).toInstant()).isEqualTo(Instant.parse("2030-12-01T20:00:00Z"));
        mvc.perform(get("/api/events/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.startsAt").value("2030-12-03T20:00:00Z"));
        mvc.perform(get("/api/events").param("from", "2030-12-01T00:00:00Z").param("to", "2030-12-02T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/events").param("from", "2030-12-03T00:00:00Z").param("to", "2030-12-04T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].startsAt").value("2030-12-03T20:00:00Z"));
        mvc.perform(get("/internal/events/recommendation-candidates"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].startsAt").value("2030-12-03T20:00:00Z"));
        mvc.perform(get("/api/management/events/" + id).header("X-User-Id", owner).header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scheduleRevision").value(1));
        var captor = org.mockito.ArgumentCaptor.forClass(EventChange.class);
        verify(notifications, times(1)).publish(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("EVENT_POSTPONED");
        assertThat(captor.getValue().previousStartsAt().toInstant()).isEqualTo(Instant.parse("2030-12-01T20:00:00Z"));
        assertThat(captor.getValue().changeId().toString()).isEqualTo(jdbc.queryForObject("select change_id from event_schedule_changes where event_id=?", UUID.class, id).toString());
    }

    @Test void rejectsBoundaryEarlyStartedForeignActorAndStaleRevisionWithoutSideEffects() throws Exception {
        UUID id = published("2026-11-23T00:00:00Z");
        long invalidations = cache.invalidations();
        assertPatch(id, "2026-11-25T00:00:00Z", 0, "boundary", owner, "ORGANIZER", 409);
        assertPatch(id, "2026-11-22T23:00:00Z", 0, "early", owner, "ORGANIZER", 409);
        assertPatch(id, "2026-11-23T00:00:00Z", 0, "equal", owner, "ORGANIZER", 409);
        assertPatch(id, "2026-11-26T00:00:00Z", 0, "foreign", UUID.randomUUID(), "ORGANIZER", 403);
        assertPatch(id, "2026-11-26T00:00:00Z", 0, "client", owner, "CLIENT", 403);
        assertPatch(id, "2026-11-26T00:00:00Z", 1, "stale", owner, "ORGANIZER", 409);
        assertThat(cache.invalidations()).isEqualTo(invalidations);
        verify(notifications, never()).publish(any());
        assertPatch(id, "2026-11-26T00:00:00Z", 0, "ok", owner, "ORGANIZER", 200);
        assertPatch(id, "2026-11-27T00:00:00Z", 0, "revision-race", owner, "ORGANIZER", 409);
        assertPatch(id, "2026-11-27T00:00:00Z", 0, "ok", owner, "ORGANIZER", 409);
        assertThat(jdbc.queryForObject("select schedule_revision from events where id=?", Long.class, id)).isEqualTo(1);
        UUID started = published("2026-11-21T00:00:00Z");
        assertPatch(started, "2026-11-27T00:00:00Z", 0, "started", owner, "ORGANIZER", 409);
        events.findById(started).ifPresent(e -> { e.setStatus(EventStatus.SUSPENDED); events.saveAndFlush(e); });
        assertPatch(started, "2026-11-27T00:00:00Z", 0, "suspended", owner, "ORGANIZER", 409);
    }

    @Test void concurrentRequestsOnSameRevisionCommitOnlyOneHistoryRow() throws Exception {
        UUID id = published("2030-12-01T20:00:00Z");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return postStatus(id, "2030-12-02T20:00:00Z", "race-a"); });
            var second = executor.submit(() -> { start.await(); return postStatus(id, "2030-12-03T20:00:00Z", "race-b"); });
            start.countDown();
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from event_schedule_changes where event_id=?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select schedule_revision from events where id=?", Long.class, id)).isEqualTo(1);
        verify(notifications, times(1)).publish(any());
    }

    private int postStatus(UUID id, String start, String key) throws Exception {
        return mvc.perform(patch("/api/management/events/" + id + "/schedule").header("X-User-Id", owner)
                        .header("X-User-Role", "ORGANIZER").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"" + start + "\",\"expectedScheduleRevision\":0}"))
                .andReturn().getResponse().getStatus();
    }

    private UUID published(String start) {
        Venue venue = new Venue("Arena", "Av 1", "Buenos Aires", BigDecimal.ZERO, BigDecimal.ZERO);
        var sector = venue.addGeneralAdmissionSector("Campo", 100);
        venues.saveAndFlush(venue);
        Event event = new Event("Show", "Live show", "MUSIC", OffsetDateTime.parse(start), owner, venue);
        event.replaceParticipants(java.util.List.of("Band"));
        event.replaceOfferings(Map.of(sector.getId(), BigDecimal.TEN));
        event.setSnapshot(EventSnapshot.copyOf(event));
        event.setStatus(EventStatus.PUBLISHED);
        UUID id = events.saveAndFlush(event).getId();
        assertThat(jdbc.queryForObject("select schedule_revision from events where id=?", Long.class, id)).isZero();
        return id;
    }

    private void assertPatch(UUID id, String start, long revision, String key, UUID actor, String role, int expected) throws Exception {
        mvc.perform(patch("/api/management/events/" + id + "/schedule").header("X-User-Id", actor).header("X-User-Role", role).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startsAt\":\"" + start + "\",\"expectedScheduleRevision\":" + revision + "}"))
                .andExpect(status().is(expected));
    }
}

