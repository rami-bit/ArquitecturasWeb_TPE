package com.ticketya.catalog.application.event;

import com.ticketya.catalog.api.error.ApiProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class EventDiscoveryService {
    public static final int MAX_PAGE_SIZE = 50;
    private final JdbcTemplate jdbc;

    public EventDiscoveryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public SearchPage search(SearchCriteria c) {
        if (c.page() == null || c.page() < 0) throw bad("page must be zero or greater");
        if (c.size() == null || c.size() < 1 || c.size() > MAX_PAGE_SIZE) throw bad("size must be between 1 and " + MAX_PAGE_SIZE);
        if (c.from() != null && c.to() != null && c.from().isAfter(c.to())) throw bad("from must be before or equal to to");
        String sort = clean(c.sort(), "date").toLowerCase(Locale.ROOT);
        String direction = clean(c.direction(), sort.equals("relevance") ? "desc" : "asc").toLowerCase(Locale.ROOT);
        if (!Set.of("date", "price", "relevance").contains(sort)) throw bad("sort must be date, price, or relevance");
        if (!Set.of("asc", "desc").contains(direction)) throw bad("direction must be asc or desc");
        String query = trimToNull(c.query());
        if (sort.equals("relevance") && query == null) throw bad("q is required when sort=relevance");

        StringBuilder where = new StringBuilder(" WHERE e.status = 'PUBLISHED'");
        List<Object> args = new ArrayList<>();
        append(where, args, c.type(), " AND e.event_type = ?");
        if (c.organizerId() != null) { where.append(" AND e.organizer_id = ?"); args.add(c.organizerId()); }
        append(where, args, trimToNull(c.participant()), " AND EXISTS (SELECT 1 FROM snapshot_participants sp WHERE sp.snapshot_id=s.id AND POSITION(LOWER(?) IN LOWER(sp.name)) > 0)");
        append(where, args, trimToNull(c.city()), " AND LOWER(s.venue_city) = LOWER(?)");
        if (c.from() != null) { where.append(" AND e.starts_at >= ?"); args.add(c.from()); }
        if (c.to() != null) { where.append(" AND e.starts_at <= ?"); args.add(c.to()); }
        if (query != null && sort.equals("relevance")) {
            where.append(" AND (POSITION(LOWER(?) IN LOWER(s.name)) > 0 OR POSITION(LOWER(?) IN LOWER(s.description)) > 0 OR EXISTS (SELECT 1 FROM snapshot_participants spq WHERE spq.snapshot_id=s.id AND POSITION(LOWER(?) IN LOWER(spq.name)) > 0))");
            args.add(query); args.add(query); args.add(query);
        }
        String order = switch (sort) {
            case "price" -> " minimum_price " + direction.toUpperCase(Locale.ROOT) + " NULLS LAST, e.id ASC";
            case "relevance" -> " relevance_score " + direction.toUpperCase(Locale.ROOT) + ", e.id ASC";
            default -> " e.starts_at " + direction.toUpperCase(Locale.ROOT) + ", e.id ASC";
        };
        String base = " FROM events e JOIN event_snapshots s ON s.event_id=e.id " + where;
        long total = jdbc.queryForObject("SELECT COUNT(DISTINCT e.id)" + base, Long.class, args.toArray());
        String relevance = query == null ? "0" : "(CASE WHEN LOWER(s.name)=LOWER(?) THEN 100 ELSE 0 END + CASE WHEN POSITION(LOWER(?) IN LOWER(s.name))=1 THEN 50 ELSE 0 END + CASE WHEN POSITION(LOWER(?) IN LOWER(s.description))>0 THEN 10 ELSE 0 END + CASE WHEN EXISTS (SELECT 1 FROM snapshot_participants spr WHERE spr.snapshot_id=s.id AND POSITION(LOWER(?) IN LOWER(spr.name))>0) THEN 20 ELSE 0 END)";
        String select = "SELECT e.id,s.name,s.event_type,s.starts_at,s.venue_city,(SELECT MIN(ss.price) FROM snapshot_sectors ss WHERE ss.snapshot_id=s.id) AS minimum_price," + relevance + " AS relevance_score" + base + " ORDER BY " + order + " LIMIT ? OFFSET ?";
        List<Object> queryArgs = new ArrayList<>();
        if (query != null) { queryArgs.add(query); queryArgs.add(query); queryArgs.add(query); queryArgs.add(query); }
        queryArgs.addAll(args); queryArgs.add(c.size()); queryArgs.add((long)c.page() * c.size());
        List<SearchItem> items = jdbc.query(select, (rs, row) -> new SearchItem(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("event_type"), rs.getObject("starts_at", OffsetDateTime.class), rs.getString("venue_city"), rs.getBigDecimal("minimum_price")), queryArgs.toArray());
        long pages = (total + c.size() - 1) / c.size();
        return new SearchPage(items, c.page(), c.size(), total, pages);
    }

    public EventDetail detail(UUID id) {
        List<EventDetail> found = jdbc.query("SELECT e.id,e.organizer_id,s.name,s.description,s.event_type,s.starts_at,s.venue_name,s.venue_address,s.venue_city,s.venue_latitude,s.venue_longitude FROM events e JOIN event_snapshots s ON s.event_id=e.id WHERE e.id=? AND e.status='PUBLISHED'", (rs, row) -> new EventDetail(rs.getObject("id", UUID.class), rs.getObject("organizer_id", UUID.class), rs.getString("name"), rs.getString("description"), rs.getString("event_type"), rs.getObject("starts_at", OffsetDateTime.class), rs.getString("venue_name"), rs.getString("venue_address"), rs.getString("venue_city"), rs.getBigDecimal("venue_latitude"), rs.getBigDecimal("venue_longitude"), List.of(), List.of()), id);
        if (found.isEmpty()) throw new ApiProblemException(HttpStatus.NOT_FOUND, "Not found", "Published event not found");
        EventDetail base = found.get(0);
        List<String> participants = jdbc.query("SELECT name FROM snapshot_participants WHERE snapshot_id=(SELECT id FROM event_snapshots WHERE event_id=?) ORDER BY name", (rs, row) -> rs.getString(1), id);
        List<SectorDetail> sectors = jdbc.query("SELECT id,source_sector_id,name,sector_type,capacity,price FROM snapshot_sectors WHERE snapshot_id=(SELECT id FROM event_snapshots WHERE event_id=?) ORDER BY name", (rs, row) -> new SectorDetail(rs.getObject("source_sector_id", UUID.class), rs.getString("name"), rs.getString("sector_type"), rs.getInt("capacity"), rs.getBigDecimal("price"), jdbc.query("SELECT row_label,seat_number FROM snapshot_seats WHERE sector_id=? ORDER BY row_label,seat_number", (seatRs, seatRow) -> new SeatDetail(seatRs.getString(1), seatRs.getInt(2)), rs.getObject("id", UUID.class))), id);
        return new EventDetail(base.id(), base.organizerId(), base.name(), base.description(), base.eventType(), base.startsAt(), base.venueName(), base.venueAddress(), base.city(), base.latitude(), base.longitude(), participants, sectors);
    }

    private static void append(StringBuilder where, List<Object> args, String value, String clause) { if (value != null && !value.isBlank()) { where.append(clause); args.add(value.trim()); } }
    private static String clean(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ApiProblemException bad(String detail) { return new ApiProblemException(HttpStatus.BAD_REQUEST, "Invalid search", detail); }

    public record SearchCriteria(String type, UUID organizerId, String participant, String city, OffsetDateTime from, OffsetDateTime to, String query, Integer page, Integer size, String sort, String direction) {}
    public record SearchItem(UUID id, String name, String eventType, OffsetDateTime startsAt, String city, BigDecimal minimumPrice) {}
    public record SearchPage(List<SearchItem> items, int page, int size, long totalElements, long totalPages) {}
    public record SeatDetail(String row, int number) {}
    public record SectorDetail(UUID id, String name, String type, int capacity, BigDecimal price, List<SeatDetail> seats) {}
    public record EventDetail(UUID id, UUID organizerId, String name, String description, String eventType, OffsetDateTime startsAt, String venueName, String venueAddress, String city, BigDecimal latitude, BigDecimal longitude, List<String> participants, List<SectorDetail> sectors) {}
}
