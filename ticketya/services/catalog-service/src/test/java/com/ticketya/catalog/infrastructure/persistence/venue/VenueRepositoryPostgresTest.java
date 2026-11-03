package com.ticketya.catalog.infrastructure.persistence.venue;

import com.ticketya.catalog.domain.venue.SeatPosition;
import com.ticketya.catalog.domain.venue.SectorType;
import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.domain.event.Event;
import com.ticketya.catalog.domain.event.EventSnapshot;
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

    @Test
    void persistsNormalizedPublicationSnapshotAndEnforcesOneSnapshotPerEvent() {
        Venue venue=venue(); var numbered=venue.addNumberedSector("Platea",List.of(new SeatPosition("A",1),new SeatPosition("A",2)));
        var general=venue.addGeneralAdmissionSector("Campo",50); venue=repository.saveAndFlush(venue);
        Event event=new Event("Festival","Music festival","MUSIC",java.time.OffsetDateTime.parse("2030-01-01T20:00:00-03:00"),UUID.randomUUID(),venue);
        event.replaceParticipants(List.of("Band")); event.replaceOfferings(java.util.Map.of(numbered.getId(),new BigDecimal("100.00"),general.getId(),new BigDecimal("50.00")));
        event.setSnapshot(EventSnapshot.copyOf(event)); event.setStatus(com.ticketya.catalog.domain.event.EventStatus.PUBLISHED);
        UUID eventId=eventRepository.saveAndFlush(event).getId(); entityManager.clear();
        assertThat(jdbcTemplate.queryForObject("select count(*) from event_snapshots where event_id=?",Integer.class,eventId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from snapshot_participants",Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from snapshot_sectors",Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from snapshot_seats",Integer.class)).isEqualTo(2);
        assertThatThrownBy(()->jdbcTemplate.update("insert into event_snapshots (id,event_id,name,description,event_type,starts_at,venue_name,venue_address,venue_city,venue_latitude,venue_longitude) select ?,event_id,name,description,event_type,starts_at,venue_name,venue_address,venue_city,venue_latitude,venue_longitude from event_snapshots where event_id=?",UUID.randomUUID(),eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void discoveryIndexesExistAndPublishedFiltersReturnOnlyMatchingSnapshotRows() {
        UUID organizer = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID published = UUID.randomUUID();
        UUID draft = UUID.randomUUID();
        UUID snapshot = UUID.randomUUID();
        UUID sector = UUID.randomUUID();
        jdbcTemplate.update("insert into venues(id,name,address,city,latitude,longitude) values(?,?,?,?,?,?)", venueId, "Arena", "Street", "Buenos Aires", BigDecimal.ZERO, BigDecimal.ZERO);
        insertEvent(published, organizer, venueId, "PUBLISHED");
        insertEvent(draft, organizer, venueId, "DRAFT");
        jdbcTemplate.update("insert into event_snapshots(id,event_id,name,description,event_type,starts_at,venue_name,venue_address,venue_city,venue_latitude,venue_longitude) values(?,?,?,?,?,?,?,?,?,?,?)", snapshot, published, "Festival", "Live music", "MUSIC", java.time.OffsetDateTime.parse("2030-06-10T20:00:00-03:00"), "Arena", "Street", "Buenos Aires", BigDecimal.ZERO, BigDecimal.ZERO);
        jdbcTemplate.update("insert into snapshot_participants(id,snapshot_id,name) values(?,?,?)", UUID.randomUUID(), snapshot, "Band");
        jdbcTemplate.update("insert into snapshot_sectors(id,snapshot_id,source_sector_id,name,sector_type,capacity,price) values(?,?,?,?,?,?,?)", sector, snapshot, UUID.randomUUID(), "Field", "GENERAL_ADMISSION", 50, new BigDecimal("25.00"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_indexes where schemaname='public' and indexname in ('ix_events_status_starts_at_id','ix_events_status_type_starts_at_id','ix_events_status_organizer_starts_at_id','ix_event_snapshots_city','ix_snapshot_participants_snapshot_name')", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForList("select s.name from events e join event_snapshots s on s.event_id=e.id where e.status='PUBLISHED' and s.event_type=? and e.organizer_id=? and s.venue_city=? and s.starts_at between ? and ? and exists (select 1 from snapshot_participants p where p.snapshot_id=s.id and lower(p.name) like lower(?))", String.class, "MUSIC", organizer, "Buenos Aires", java.time.OffsetDateTime.parse("2030-06-10T20:00:00-03:00"), java.time.OffsetDateTime.parse("2030-06-10T20:00:00-03:00"), "%band%")).containsExactly("Festival");
    }

    private void insertEvent(UUID eventId, UUID organizerId, UUID venueId, String status) {
        jdbcTemplate.update("insert into events(id,name,description,event_type,starts_at,organizer_id,venue_id,status) values(?,?,?,?,?,?,?,?)", eventId, "Festival", "Live music", "MUSIC", java.time.OffsetDateTime.parse("2030-06-10T20:00:00-03:00"), organizerId, venueId, status);
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
