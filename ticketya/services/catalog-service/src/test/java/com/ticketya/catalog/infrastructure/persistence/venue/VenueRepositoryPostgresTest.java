package com.ticketya.catalog.infrastructure.persistence.venue;

import com.ticketya.catalog.domain.venue.SeatPosition;
import com.ticketya.catalog.domain.venue.SectorType;
import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.domain.event.Event;
import com.ticketya.catalog.infrastructure.persistence.event.EventRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class VenueRepositoryPostgresTest {
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
    private VenueRepository repository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsNumberedSeatsAndGeneralAdmissionSector() {
        Venue venue = venue();
        venue.addNumberedSector(
                "Platea",
                List.of(new SeatPosition("A", 1), new SeatPosition("A", 2)));
        venue.addGeneralAdmissionSector("Campo", 500);

        UUID venueId = repository.saveAndFlush(venue).getId();
        entityManager.clear();

        Venue persisted = repository.findById(venueId).orElseThrow();
        assertThat(persisted.getSectors()).hasSize(2);
        assertThat(persisted.getSectors())
                .filteredOn(sector -> sector.getType() == SectorType.NUMBERED)
                .singleElement()
                .satisfies(sector -> {
                    assertThat(sector.getCapacity()).isEqualTo(2);
                    assertThat(sector.getSeats()).extracting("rowLabel", "seatNumber")
                            .containsExactly(tuple("A", 1), tuple("A", 2));
                });
        assertThat(persisted.getSectors())
                .filteredOn(sector -> sector.getType() == SectorType.GENERAL_ADMISSION)
                .singleElement()
                .satisfies(sector -> assertThat(sector.getCapacity()).isEqualTo(500));
    }

    @Test
    void databaseRejectsDuplicateSeatPosition() {
        Venue venue = venue();
        var sector = venue.addNumberedSector("Platea", List.of(new SeatPosition("A", 1)));
        repository.saveAndFlush(venue);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO venue_seats (id, venue_sector_id, row_label, seat_number) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), sector.getId(), "A", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNonPositiveGeneralCapacity() {
        Venue venue = repository.saveAndFlush(venue());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO venue_sectors (id, venue_id, name, sector_type, general_capacity) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), venue.getId(), "Campo", "GENERAL_ADMISSION", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsEventParticipantsAndDecimalOfferingsAndEnforcesEventConstraints() {
        Venue venue = venue();
        var sector = venue.addGeneralAdmissionSector("Campo", 100);
        venue = repository.saveAndFlush(venue);
        UUID venueId = venue.getId();
        Event event = new Event("Festival", "Music festival", "MUSIC",
                java.time.OffsetDateTime.parse("2030-01-01T20:00:00-03:00"), UUID.randomUUID(), venue);
        event.replaceParticipants(List.of("Band"));
        event.replaceOfferings(java.util.Map.of(sector.getId(), new BigDecimal("1250.50")));
        UUID eventId = eventRepository.saveAndFlush(event).getId();
        entityManager.clear();
        Event persisted = eventRepository.findById(eventId).orElseThrow();
        assertThat(persisted.getStatus().name()).isEqualTo("DRAFT");
        assertThat(persisted.getParticipants()).extracting("name").containsExactly("Band");
        assertThat(persisted.getOfferings()).singleElement().satisfies(o ->
                assertThat(o.getPrice()).isEqualByComparingTo("1250.50"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO event_sector_offerings (id,event_id,venue_sector_id,price) VALUES (?,?,?,?)",
                UUID.randomUUID(), eventId, sector.getId(), new BigDecimal("0.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnsupportedEventStatus() {
        Venue venue = repository.saveAndFlush(venue());
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO events (id,name,description,event_type,starts_at,organizer_id,venue_id,status) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), "Invalid", "Invalid", "MUSIC", java.time.OffsetDateTime.now(), UUID.randomUUID(), venue.getId(), "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Venue venue() {
        return new Venue(
                "Estadio",
                "Av. Siempre Viva 123",
                "Buenos Aires",
                BigDecimal.valueOf(-34.6037),
                BigDecimal.valueOf(-58.3816));
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
