package com.ticketya.catalog.infrastructure.persistence.event;

import com.ticketya.catalog.domain.event.Event;
import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.infrastructure.persistence.venue.VenueRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica contra PostgreSQL real que la marca de alta demanda quedó en el
 * esquema con {@code NOT NULL DEFAULT FALSE}: los INSERT crudos existentes
 * siguen siendo válidos y el repositorio JPA persiste la marca.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class HighDemandPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private VenueRepository venues;

    @Autowired
    private EventRepository events;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void highDemandColumnDefaultsToFalseAndRejectsNull() {
        Venue venue = venues.saveAndFlush(venue());
        OffsetDateTime startsAt = OffsetDateTime.parse("2030-01-01T20:00:00-03:00");
        UUID organizer = UUID.randomUUID();

        UUID implicit = UUID.randomUUID();
        jdbc.update("insert into events(id,name,description,event_type,starts_at,organizer_id,venue_id,status) "
                        + "values(?,?,?,?,?,?,?,?)",
                implicit, "Show", "Live show", "MUSIC", startsAt, organizer, venue.getId(), "DRAFT");
        assertThat(jdbc.queryForObject("select high_demand from events where id=?", Boolean.class, implicit))
                .as("el INSERT crudo sin la columna queda válido con DEFAULT FALSE")
                .isFalse();

        UUID explicit = UUID.randomUUID();
        jdbc.update("insert into events(id,name,description,event_type,starts_at,organizer_id,venue_id,status,high_demand) "
                        + "values(?,?,?,?,?,?,?,?,?)",
                explicit, "Show", "Live show", "MUSIC", startsAt, organizer, venue.getId(), "DRAFT", true);
        assertThat(jdbc.queryForObject("select high_demand from events where id=?", Boolean.class, explicit))
                .isTrue();

        assertThatThrownBy(() -> jdbc.update(
                "insert into events(id,name,description,event_type,starts_at,organizer_id,venue_id,status,high_demand) "
                        + "values(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), "Show", "Live show", "MUSIC", startsAt, organizer, venue.getId(), "DRAFT", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void eventRepositoryPersistsTheHighDemandFlag() {
        Venue venue = venues.saveAndFlush(venue());
        Event event = new Event("Festival", "Music festival", "MUSIC",
                OffsetDateTime.parse("2030-01-01T20:00:00-03:00"), UUID.randomUUID(), venue);
        event.setHighDemand(true);

        UUID eventId = events.saveAndFlush(event).getId();
        entityManager.clear();

        assertThat(events.findById(eventId).orElseThrow().isHighDemand()).isTrue();
    }

    private Venue venue() {
        return new Venue("Estadio", "Av. Siempre Viva 123", "Buenos Aires",
                BigDecimal.valueOf(-34.6037), BigDecimal.valueOf(-58.3816));
    }
}
