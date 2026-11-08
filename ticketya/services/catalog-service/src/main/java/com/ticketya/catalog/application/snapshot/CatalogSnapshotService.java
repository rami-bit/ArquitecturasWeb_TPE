package com.ticketya.catalog.application.snapshot;

import com.ticketya.catalog.api.error.ApiProblemException;
import com.ticketya.catalog.api.snapshot.CatalogEventSnapshot;
import com.ticketya.catalog.api.snapshot.SnapshotSeat;
import com.ticketya.catalog.api.snapshot.SnapshotSector;
import com.ticketya.catalog.api.snapshot.SnapshotVenue;
import com.ticketya.catalog.domain.venue.SectorType;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reads only the immutable publication copy. It never recomputes layout data from
 * the venue template and never exposes prices.
 */
@Service
@Transactional(readOnly = true)
public class CatalogSnapshotService {

    private final JdbcTemplate jdbc;

    public CatalogSnapshotService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CatalogEventSnapshot getSnapshot(UUID eventId) {
        List<SnapshotHeader> headers = jdbc.query("""
                        SELECT e.id AS event_id,
                               s.id AS snapshot_id,
                               s.name,
                               s.description,
                               s.event_type,
                               s.starts_at,
                               s.venue_name,
                               s.venue_address,
                               s.venue_city,
                               s.venue_latitude,
                               s.venue_longitude
                        FROM events e
                        JOIN event_snapshots s ON s.event_id = e.id
                        WHERE e.id = ? AND e.status = 'PUBLISHED'
                        """,
                (rs, rowNum) -> new SnapshotHeader(
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("snapshot_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("event_type"),
                        rs.getObject("starts_at", OffsetDateTime.class),
                        rs.getString("venue_name"),
                        rs.getString("venue_address"),
                        rs.getString("venue_city"),
                        rs.getBigDecimal("venue_latitude"),
                        rs.getBigDecimal("venue_longitude")),
                eventId);

        if (headers.isEmpty()) {
            throw new ApiProblemException(HttpStatus.NOT_FOUND, "Not found", "Published event not found");
        }

        SnapshotHeader header = headers.get(0);
        List<SnapshotSector> sectors = jdbc.query("""
                        SELECT id, source_sector_id, name, sector_type, capacity
                        FROM snapshot_sectors
                        WHERE snapshot_id = ?
                        ORDER BY name ASC, id ASC
                        """,
                (rs, rowNum) -> new SectorHeader(
                        rs.getObject("id", UUID.class),
                        rs.getObject("source_sector_id", UUID.class),
                        rs.getString("name"),
                        SectorType.valueOf(rs.getString("sector_type")),
                        rs.getInt("capacity")),
                header.snapshotId()).stream()
                .map(sector -> new SnapshotSector(
                        sector.sourceSectorId(),
                        sector.name(),
                        sector.type(),
                        sector.capacity(),
                        seats(sector.id())))
                .toList();

        return new CatalogEventSnapshot(
                header.eventId(),
                header.snapshotId(),
                header.name(),
                header.description(),
                header.eventType(),
                header.startsAt(),
                new SnapshotVenue(
                        header.venueName(),
                        header.venueAddress(),
                        header.venueCity(),
                        header.venueLatitude(),
                        header.venueLongitude()),
                sectors);
    }

    private List<SnapshotSeat> seats(UUID sectorId) {
        return jdbc.query("""
                        SELECT source_seat_id, row_label, seat_number
                        FROM snapshot_seats
                        WHERE sector_id = ?
                        ORDER BY row_label ASC, seat_number ASC
                        """,
                (rs, rowNum) -> new SnapshotSeat(
                        rs.getObject("source_seat_id", UUID.class),
                        rs.getString("row_label"),
                        rs.getInt("seat_number")),
                sectorId);
    }

    private record SnapshotHeader(
            UUID eventId,
            UUID snapshotId,
            String name,
            String description,
            String eventType,
            OffsetDateTime startsAt,
            String venueName,
            String venueAddress,
            String venueCity,
            BigDecimal venueLatitude,
            BigDecimal venueLongitude) {
    }

    private record SectorHeader(
            UUID id,
            UUID sourceSectorId,
            String name,
            SectorType type,
            int capacity) {
    }
}
