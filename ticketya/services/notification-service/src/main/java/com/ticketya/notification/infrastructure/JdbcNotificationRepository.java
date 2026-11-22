package com.ticketya.notification.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.notification.api.inbox.NotificationView;
import com.ticketya.notification.application.TriggerType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbox ({@code notification_triggers}) y outbox ({@code notifications}).
 * Los reclamos usan {@code FOR UPDATE SKIP LOCKED} y un arrendamiento sobre
 * {@code next_attempt_at}: con varias instancias, cada fila la procesa una sola.
 */
@Repository
public class JdbcNotificationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcNotificationRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record ClaimedTrigger(UUID id, TriggerType type, JsonNode payload, String correlationId, int attempts) {
    }

    public record ClaimedNotification(UUID id, UUID customerId, TriggerType type, String channel, String subject,
                                      String body, String correlationId, int attempts) {
    }

    // ------------------------------------------------------------------ inbox

    /** Inserta el disparador; devuelve {@code false} si su clave ya existía. */
    public boolean insertTrigger(UUID id, String dedupeKey, TriggerType type, JsonNode payload, String correlationId) {
        return jdbc.update("""
                INSERT INTO notification_triggers (id, dedupe_key, type, payload, correlation_id)
                VALUES (?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (dedupe_key) DO NOTHING
                """, id, dedupeKey, type.name(), write(payload), correlationId) == 1;
    }

    public UUID triggerIdByDedupeKey(String dedupeKey) {
        return jdbc.queryForObject("SELECT id FROM notification_triggers WHERE dedupe_key = ?", UUID.class, dedupeKey);
    }

    public List<ClaimedTrigger> claimTriggers(int limit, int leaseSeconds) {
        return jdbc.query("""
                UPDATE notification_triggers
                SET attempts = attempts + 1,
                    next_attempt_at = CURRENT_TIMESTAMP + make_interval(secs => ?)
                WHERE id IN (
                    SELECT id FROM notification_triggers
                    WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)
                RETURNING id, type, payload::text AS payload, correlation_id, attempts
                """, (rs, n) -> new ClaimedTrigger(
                rs.getObject("id", UUID.class),
                TriggerType.valueOf(rs.getString("type")),
                read(rs.getString("payload")),
                rs.getString("correlation_id"),
                rs.getInt("attempts")), leaseSeconds, limit);
    }

    public void completeTrigger(UUID id, int recipients) {
        jdbc.update("""
                UPDATE notification_triggers SET status = 'PROCESSED', recipients = ?, processed_at = CURRENT_TIMESTAMP,
                    last_error = NULL
                WHERE id = ?
                """, recipients, id);
    }

    public void retryTrigger(UUID id, long delayMs, String error) {
        jdbc.update("""
                UPDATE notification_triggers SET next_attempt_at = CURRENT_TIMESTAMP + make_interval(secs => ?),
                    last_error = ?
                WHERE id = ?
                """, delayMs / 1000.0, truncate(error), id);
    }

    public void failTrigger(UUID id, String error) {
        jdbc.update("UPDATE notification_triggers SET status = 'FAILED', last_error = ? WHERE id = ?",
                truncate(error), id);
    }

    // ------------------------------------------------------------------ outbox

    public boolean insertNotification(UUID triggerId, UUID customerId, TriggerType type, String channel,
                                      String subject, String body, UUID eventId, UUID orderId, String correlationId) {
        return jdbc.update("""
                INSERT INTO notifications (id, trigger_id, customer_id, type, channel, subject, body, event_id,
                    order_id, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (trigger_id, customer_id, channel) DO NOTHING
                """, UUID.randomUUID(), triggerId, customerId, type.name(), channel, subject, body, eventId,
                orderId, correlationId) == 1;
    }

    public List<ClaimedNotification> claimNotifications(int limit, int leaseSeconds) {
        return jdbc.query("""
                UPDATE notifications
                SET attempts = attempts + 1,
                    next_attempt_at = CURRENT_TIMESTAMP + make_interval(secs => ?)
                WHERE id IN (
                    SELECT id FROM notifications
                    WHERE status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP
                    ORDER BY created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED)
                RETURNING id, customer_id, type, channel, subject, body, correlation_id, attempts
                """, (rs, n) -> new ClaimedNotification(
                rs.getObject("id", UUID.class),
                rs.getObject("customer_id", UUID.class),
                TriggerType.valueOf(rs.getString("type")),
                rs.getString("channel"),
                rs.getString("subject"),
                rs.getString("body"),
                rs.getString("correlation_id"),
                rs.getInt("attempts")), leaseSeconds, limit);
    }

    public void markSent(UUID id) {
        jdbc.update("""
                UPDATE notifications SET status = 'SENT', sent_at = CURRENT_TIMESTAMP, last_error = NULL WHERE id = ?
                """, id);
    }

    public void markSkippedNoContact(UUID id) {
        jdbc.update("UPDATE notifications SET status = 'SKIPPED_NO_CONTACT', last_error = 'No email contact registered' WHERE id = ?", id);
    }

    public Optional<String> emailForCustomer(UUID customerId) {
        return jdbc.query("SELECT email FROM customer_email_contacts WHERE customer_id = ?",
                (rs, n) -> rs.getString("email"), customerId).stream().findFirst();
    }

    public void upsertEmail(UUID customerId, String email) {
        jdbc.update("""
                INSERT INTO customer_email_contacts (customer_id, email) VALUES (?, ?)
                ON CONFLICT (customer_id) DO UPDATE SET email = EXCLUDED.email, updated_at = CURRENT_TIMESTAMP
                """, customerId, email);
    }

    public void retryNotification(UUID id, long delayMs, String error) {
        jdbc.update("""
                UPDATE notifications SET next_attempt_at = CURRENT_TIMESTAMP + make_interval(secs => ?), last_error = ?
                WHERE id = ?
                """, delayMs / 1000.0, truncate(error), id);
    }

    public void failNotification(UUID id, String error) {
        jdbc.update("UPDATE notifications SET status = 'FAILED', last_error = ? WHERE id = ?", truncate(error), id);
    }

    // ------------------------------------------------------------------ consulta

    public List<NotificationView> findForCustomer(UUID customerId, String type, int page, int size) {
        List<Object> args = new ArrayList<>(List.of(customerId));
        String filter = filter(type, args);
        args.add(size);
        args.add((long) page * size);
        return jdbc.query("""
                SELECT id, type, channel, subject, body, status, event_id, order_id, correlation_id, attempts,
                       created_at, sent_at
                FROM notifications WHERE customer_id = ?""" + filter + """
                 ORDER BY created_at DESC, id LIMIT ? OFFSET ?
                """, (rs, n) -> new NotificationView(
                rs.getObject("id", UUID.class),
                rs.getString("type"),
                rs.getString("channel"),
                rs.getString("subject"),
                rs.getString("body"),
                rs.getString("status"),
                rs.getObject("event_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("correlation_id"),
                rs.getInt("attempts"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("sent_at", OffsetDateTime.class)), args.toArray());
    }

    public long countForCustomer(UUID customerId, String type) {
        List<Object> args = new ArrayList<>(List.of(customerId));
        String filter = filter(type, args);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE customer_id = ?" + filter,
                Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    public Optional<String> triggerStatus(UUID triggerId) {
        return jdbc.query("SELECT status FROM notification_triggers WHERE id = ?",
                (rs, n) -> rs.getString("status"), triggerId).stream().findFirst();
    }

    private static String filter(String type, List<Object> args) {
        if (type == null || type.isBlank()) {
            return "";
        }
        args.add(type.trim());
        return " AND type = ?";
    }

    private String write(JsonNode payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("payload is not serializable", exception);
        }
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException exception) {
            return mapper.createObjectNode();
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 300 ? value : value.substring(0, 300);
    }
}
