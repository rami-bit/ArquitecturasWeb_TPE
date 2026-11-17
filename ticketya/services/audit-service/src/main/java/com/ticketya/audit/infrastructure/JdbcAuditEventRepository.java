package com.ticketya.audit.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.audit.api.events.AuditEventRequest;
import com.ticketya.audit.api.events.AuditEventView;
import com.ticketya.audit.domain.AuditSearchCriteria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persistencia append-only de la bitácora en PostgreSQL. */
@Repository
public class JdbcAuditEventRepository {
    private static final String COLUMNS = """
            id, correlation_id, service, operation, outcome, actor_id, actor_role,
            resource_type, resource_id, details::text AS details, occurred_at, received_at
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<AuditEventView> rowMapper;

    public JdbcAuditEventRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.rowMapper = (rs, n) -> new AuditEventView(
                rs.getObject("id", UUID.class),
                rs.getString("correlation_id"),
                rs.getString("service"),
                rs.getString("operation"),
                rs.getString("outcome"),
                rs.getString("actor_id"),
                rs.getString("actor_role"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getObject("received_at", OffsetDateTime.class),
                readDetails(rs.getString("details")));
    }

    /** Inserta el evento; devuelve {@code false} si el eventId ya estaba registrado. */
    public boolean insertIfAbsent(AuditEventRequest event) {
        int inserted = jdbc.update("""
                INSERT INTO audit_events (id, correlation_id, service, operation, outcome, actor_id,
                    actor_role, resource_type, resource_id, details, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                event.eventId(), event.correlationId().trim(), event.service().trim(),
                event.operation(), event.outcome().trim(), blankToNull(event.actorId()),
                blankToNull(event.actorRole()), blankToNull(event.resourceType()),
                blankToNull(event.resourceId()), writeDetails(event.details()), event.occurredAt());
        return inserted == 1;
    }

    public List<AuditEventView> findByCorrelationId(String correlationId) {
        return jdbc.query("SELECT " + COLUMNS
                        + " FROM audit_events WHERE correlation_id = ? ORDER BY occurred_at ASC, received_at ASC",
                rowMapper, correlationId);
    }

    public List<AuditEventView> search(AuditSearchCriteria criteria) {
        List<Object> args = new ArrayList<>();
        String where = where(criteria, args);
        args.add(criteria.size());
        args.add((long) criteria.page() * criteria.size());
        return jdbc.query("SELECT " + COLUMNS + " FROM audit_events" + where
                + " ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?", rowMapper, args.toArray());
    }

    public long count(AuditSearchCriteria criteria) {
        List<Object> args = new ArrayList<>();
        String where = where(criteria, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events" + where, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private static String where(AuditSearchCriteria criteria, List<Object> args) {
        List<String> conditions = new ArrayList<>();
        addEquals(conditions, args, "correlation_id", criteria.correlationId());
        addEquals(conditions, args, "service", criteria.service());
        addEquals(conditions, args, "operation", criteria.operation());
        addEquals(conditions, args, "outcome", criteria.outcome());
        addEquals(conditions, args, "resource_id", criteria.resourceId());
        addEquals(conditions, args, "actor_id", criteria.actorId());
        if (criteria.from() != null) {
            conditions.add("occurred_at >= ?");
            args.add(criteria.from());
        }
        if (criteria.to() != null) {
            conditions.add("occurred_at <= ?");
            args.add(criteria.to());
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private static void addEquals(List<String> conditions, List<Object> args, String column, String value) {
        if (value != null && !value.isBlank()) {
            conditions.add(column + " = ?");
            args.add(value.trim());
        }
    }

    private String writeDetails(JsonNode details) {
        if (details == null || details.isNull()) {
            return "{}";
        }
        try {
            return mapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("details must be valid JSON", exception);
        }
    }

    private JsonNode readDetails(String json) {
        try {
            return mapper.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException exception) {
            return mapper.createObjectNode();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
