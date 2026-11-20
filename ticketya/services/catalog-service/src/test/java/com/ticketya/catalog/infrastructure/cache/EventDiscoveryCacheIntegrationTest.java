package com.ticketya.catalog.infrastructure.cache;

import com.ticketya.catalog.application.event.EventDiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La caché sirve el snapshot publicado hasta que se invalida; la base es la verdad. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:discovery-cache;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "catalog.cache.ttl-seconds=30"
})
class EventDiscoveryCacheIntegrationTest {

    @Autowired
    private EventDiscoveryService discovery;

    @Autowired
    private EventDiscoveryCache cache;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cache.invalidateAll();
        jdbc.update("delete from snapshot_sectors");
        jdbc.update("delete from snapshot_participants");
        jdbc.update("delete from event_snapshots");
        jdbc.update("delete from events");
        jdbc.update("delete from venues");
        seed();
    }

    @Test
    void busquedaCacheadaSeActualizaTrasInvalidacion() {
        EventDiscoveryService.SearchCriteria criteria = new EventDiscoveryService.SearchCriteria(
                null, null, null, "Buenos Aires", null, null, null, 0, 10, "date", "asc");

        assertThat(discovery.search(criteria).totalElements()).isEqualTo(1);
        jdbc.update("update events set status='SUSPENDED' where id=?", eventId);

        assertThat(discovery.search(criteria).totalElements())
                .as("dentro del TTL se sirve la copia cacheada")
                .isEqualTo(1);

        cache.invalidateAll();
        assertThat(discovery.search(criteria).totalElements()).isZero();
    }

    @Test
    void detalleCacheadoNoIncluyeDisponibilidad() {
        var detail = discovery.detail(eventId);

        assertThat(detail.name()).isEqualTo("Evento cacheado");
        assertThat(detail.sectors()).hasSize(1);
        assertThat(detail.sectors().get(0).capacity()).isEqualTo(100);
    }

    private void seed() {
        UUID venue = UUID.randomUUID();
        UUID snapshot = UUID.randomUUID();
        UUID sector = UUID.randomUUID();
        OffsetDateTime startsAt = OffsetDateTime.parse("2030-06-10T20:00:00-03:00");
        jdbc.update("insert into venues(id,name,address,city,latitude,longitude) values(?,?,?,?,?,?)",
                venue, "Venue", "Street 1", "Buenos Aires", BigDecimal.ZERO, BigDecimal.ZERO);
        jdbc.update("insert into events(id,name,description,event_type,starts_at,organizer_id,venue_id,status,high_demand) "
                        + "values(?,?,?,?,?,?,?,?,?)",
                eventId, "Evento cacheado", "Descripcion", "MUSIC", startsAt,
                UUID.randomUUID(), venue, "PUBLISHED", false);
        jdbc.update("insert into event_snapshots(id,event_id,name,description,event_type,starts_at,"
                        + "venue_name,venue_address,venue_city,venue_latitude,venue_longitude) "
                        + "values(?,?,?,?,?,?,?,?,?,?,?)",
                snapshot, eventId, "Evento cacheado", "Descripcion", "MUSIC", startsAt,
                "Venue", "Street 1", "Buenos Aires", BigDecimal.ZERO, BigDecimal.ZERO);
        jdbc.update("insert into snapshot_participants(id,snapshot_id,name) values(?,?,?)",
                UUID.randomUUID(), snapshot, "Artista");
        jdbc.update("insert into snapshot_sectors(id,snapshot_id,source_sector_id,name,sector_type,capacity,price) "
                        + "values(?,?,?,?,?,?,?)",
                sector, snapshot, UUID.randomUUID(), "Campo", "GENERAL_ADMISSION", 100,
                new BigDecimal("10.00"));
    }
}
