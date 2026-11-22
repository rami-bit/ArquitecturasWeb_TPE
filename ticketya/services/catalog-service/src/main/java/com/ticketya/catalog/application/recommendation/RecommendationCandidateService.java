package com.ticketya.catalog.application.recommendation;

import com.ticketya.catalog.api.recommendation.EventCandidatePageResponse;
import com.ticketya.catalog.api.recommendation.EventCandidateResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Candidatos para recomendaciones sobre el snapshot publicado del catálogo.
 * Aplica el mismo criterio de visibilidad del descubrimiento (`PUBLISHED` y
 * fecha futura), sin exponer disponibilidad, cupos ni stock.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationCandidateService {

    public static final int MAX_LIMIT = 200;

    private final JdbcTemplate jdbc;

    public RecommendationCandidateService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EventCandidatePageResponse candidates(int limit) {
        String sql = """
                SELECT s.id AS snapshot_id, s.event_id AS event_id, s.name AS name,
                       s.event_type AS event_type, e.starts_at AS starts_at,
                       s.venue_city AS city,
                       (SELECT MIN(ss.price) FROM snapshot_sectors ss
                         WHERE ss.snapshot_id = s.id) AS min_price
                FROM events e JOIN event_snapshots s ON s.event_id = e.id
                WHERE e.status = 'PUBLISHED' AND e.starts_at > ?
                ORDER BY e.starts_at ASC, s.event_id ASC
                LIMIT ?
                """;
        List<CandidateRow> rows = jdbc.query(sql,
                (rs, index) -> new CandidateRow(
                        rs.getObject("snapshot_id", UUID.class),
                        rs.getObject("event_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("event_type"),
                        rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                        rs.getString("city"),
                        rs.getBigDecimal("min_price")),
                OffsetDateTime.now(), limit);
        Map<UUID, List<String>> participants = participantsBySnapshot(rows);
        List<EventCandidateResponse> items = new ArrayList<>();
        for (CandidateRow row : rows) {
            items.add(new EventCandidateResponse(row.eventId(), row.name(), row.eventType(),
                    row.startsAt(), row.city(),
                    participants.getOrDefault(row.snapshotId(), List.of()), row.minPrice()));
        }
        return new EventCandidatePageResponse(List.copyOf(items), Instant.now());
    }

    private Map<UUID, List<String>> participantsBySnapshot(List<CandidateRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
        String sql = "SELECT snapshot_id, name FROM snapshot_participants "
                + "WHERE snapshot_id IN (" + placeholders + ") ORDER BY snapshot_id, name";
        List<Object> args = new ArrayList<>(rows.stream().map(CandidateRow::snapshotId).toList());
        Map<UUID, List<String>> result = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            result.computeIfAbsent(rs.getObject("snapshot_id", UUID.class), key -> new ArrayList<>())
                    .add(rs.getString("name"));
        }, args.toArray());
        return result;
    }

    private record CandidateRow(UUID snapshotId, UUID eventId, String name, String eventType,
                                Instant startsAt, String city, java.math.BigDecimal minPrice) {
    }
}
