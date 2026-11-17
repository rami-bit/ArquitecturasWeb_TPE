package com.ticketya.queue.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL store for the virtual queue. Position allocation and the lazy batch
 * promotion run inside one transaction guarded by a per-event advisory lock,
 * mirroring {@code InventoryStore.lockEvent}.
 */
@Repository
public class QueueStore {

    public record EntryRow(UUID id, UUID eventId, UUID clientId, long position, String status,
                           OffsetDateTime joinedAt, OffsetDateTime admittedAt,
                           OffsetDateTime admissionExpiresAt, OffsetDateTime consumedAt,
                           String idempotencyKey) {
    }

    private static final String ENTRY_COLUMNS = """
            id, event_id, client_id, position, status, joined_at,
            admitted_at, admission_expires_at, consumed_at, idempotency_key
            """;

    private static final RowMapper<EntryRow> ENTRY_MAPPER = (rs, row) -> new EntryRow(
            rs.getObject("id", UUID.class),
            rs.getObject("event_id", UUID.class),
            rs.getObject("client_id", UUID.class),
            rs.getLong("position"),
            rs.getString("status"),
            rs.getObject("joined_at", OffsetDateTime.class),
            rs.getObject("admitted_at", OffsetDateTime.class),
            rs.getObject("admission_expires_at", OffsetDateTime.class),
            rs.getObject("consumed_at", OffsetDateTime.class),
            rs.getString("idempotency_key"));

    private final JdbcTemplate jdbc;

    public QueueStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // --------------------------------------------------------------------- identity

    /** Serializes allocation and batch promotion for one event across instances. */
    public void lockEvent(UUID eventId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> null, eventId.toString());
    }

    // --------------------------------------------------------------------- counters

    public void upsertCounter(UUID eventId) {
        jdbc.update("""
                INSERT INTO queue_counters (event_id, next_position, opened_at)
                VALUES (?, 1, now())
                ON CONFLICT (event_id) DO NOTHING
                """, eventId);
    }

    public Optional<OffsetDateTime> findOpenedAt(UUID eventId) {
        return jdbc.query("SELECT opened_at FROM queue_counters WHERE event_id = ?",
                        (rs, row) -> rs.getObject("opened_at", OffsetDateTime.class), eventId)
                .stream().findFirst();
    }

    public Long readNextPositionForUpdate(UUID eventId) {
        return jdbc.query(
                "SELECT next_position FROM queue_counters WHERE event_id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getLong("next_position") : null, eventId);
    }

    public void bumpCounter(UUID eventId) {
        jdbc.update("UPDATE queue_counters SET next_position = next_position + 1 WHERE event_id = ?",
                eventId);
    }

    // ---------------------------------------------------------------------- entries

    public void insertEntry(UUID id, UUID eventId, UUID clientId, long position) {
        jdbc.update("""
                INSERT INTO queue_entries (id, event_id, client_id, position, status, joined_at)
                VALUES (?, ?, ?, ?, 'WAITING', now())
                """, id, eventId, clientId, position);
    }

    public Optional<EntryRow> findActiveEntry(UUID eventId, UUID clientId) {
        return jdbc.query("""
                        SELECT %s FROM queue_entries
                        WHERE event_id = ? AND client_id = ? AND status IN ('WAITING', 'ADMITTED')
                        """.formatted(ENTRY_COLUMNS), ENTRY_MAPPER, eventId, clientId)
                .stream().findFirst();
    }

    /** The newest entry wins: a current admission always has the highest position. */
    public Optional<EntryRow> findEntry(UUID eventId, UUID clientId) {
        return jdbc.query("""
                        SELECT %s FROM queue_entries
                        WHERE event_id = ? AND client_id = ?
                        ORDER BY position DESC
                        LIMIT 1
                        """.formatted(ENTRY_COLUMNS), ENTRY_MAPPER, eventId, clientId)
                .stream().findFirst();
    }

    public long countEntries(UUID eventId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM queue_entries WHERE event_id = ?", Long.class, eventId);
        return count == null ? 0 : count;
    }

    public long countActiveAhead(UUID eventId, long position) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM queue_entries
                WHERE event_id = ? AND position < ? AND status IN ('WAITING', 'ADMITTED')
                """, Long.class, eventId, position);
        return count == null ? 0 : count;
    }

    public int expireDueEntries(UUID eventId) {
        return jdbc.update("""
                UPDATE queue_entries SET status = 'EXPIRED'
                WHERE event_id = ? AND status = 'ADMITTED'
                  AND admission_expires_at IS NOT NULL
                  AND admission_expires_at <= now()
                """, eventId);
    }

    public int promoteThrough(UUID eventId, long admittedThrough, int ttlSeconds) {
        return jdbc.update("""
                UPDATE queue_entries
                SET status = 'ADMITTED',
                    admitted_at = now(),
                    admission_expires_at = now() + (? * interval '1 second')
                WHERE event_id = ? AND status = 'WAITING' AND position <= ?
                """, ttlSeconds, eventId, admittedThrough);
    }

    public int leave(UUID eventId, UUID clientId) {
        return jdbc.update("""
                UPDATE queue_entries SET status = 'LEFT'
                WHERE event_id = ? AND client_id = ? AND status IN ('WAITING', 'ADMITTED')
                """, eventId, clientId);
    }

    public int consume(UUID entryId, String idempotencyKey) {
        return jdbc.update("""
                UPDATE queue_entries
                SET status = 'CONSUMED', consumed_at = now(), idempotency_key = ?
                WHERE id = ? AND status = 'ADMITTED'
                """, idempotencyKey, entryId);
    }
}
