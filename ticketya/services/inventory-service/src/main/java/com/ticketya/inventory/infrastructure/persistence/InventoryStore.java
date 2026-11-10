package com.ticketya.inventory.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL store for the availability-critical operations. Conditional updates are the
 * mechanism that prevents double selling; no read-then-decide logic is used for stock.
 */
@Repository
public class InventoryStore {
    private final JdbcTemplate jdbc;

    public InventoryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record InventoryRow(UUID id, UUID eventId, UUID snapshotId, int holdTtlSeconds) {
    }

    public record SectorRow(UUID id, UUID sourceSectorId, String name, String type,
                            int capacity, int sold, int reserved) {
    }

    public record ItemRow(UUID id, UUID sectorId, String rowLabel, int seatNumber, String status) {
    }

    public record ReservationRow(UUID id, UUID orderId, UUID eventInventoryId, UUID eventId,
                                 String status, OffsetDateTime expiresAt,
                                 OffsetDateTime createdAt, String fingerprint) {
    }

    public record ReservationItemRow(UUID sectorId, UUID inventoryItemId, int quantity) {
    }

    private static final RowMapper<InventoryRow> INVENTORY_MAPPER = (rs, row) -> new InventoryRow(
            rs.getObject("id", UUID.class),
            rs.getObject("event_id", UUID.class),
            rs.getObject("snapshot_id", UUID.class),
            rs.getInt("hold_ttl_seconds"));

    private static final RowMapper<SectorRow> SECTOR_MAPPER = (rs, row) -> new SectorRow(
            rs.getObject("id", UUID.class),
            rs.getObject("source_sector_id", UUID.class),
            rs.getString("name"),
            rs.getString("type"),
            rs.getInt("capacity"),
            rs.getInt("sold"),
            rs.getInt("reserved"));

    private static final RowMapper<ItemRow> ITEM_MAPPER = (rs, row) -> new ItemRow(
            rs.getObject("id", UUID.class),
            rs.getObject("sector_id", UUID.class),
            rs.getString("row_label"),
            rs.getInt("seat_number"),
            rs.getString("status"));

    private static final RowMapper<ReservationRow> RESERVATION_MAPPER = (rs, row) -> new ReservationRow(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getObject("event_inventory_id", UUID.class),
            rs.getObject("event_id", UUID.class),
            rs.getString("status"),
            rs.getObject("expires_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getString("request_fingerprint"));

    private static final String RESERVATION_SELECT = """
            SELECT r.id, r.order_id, r.event_inventory_id, i.event_id, r.status,
                   r.expires_at, r.created_at, r.request_fingerprint
            FROM reservations r
            JOIN event_inventories i ON i.id = r.event_inventory_id
            """;

    // ------------------------------------------------------------------ inventory

    public Optional<InventoryRow> findInventory(UUID eventId) {
        return jdbc.query("SELECT id, event_id, snapshot_id, hold_ttl_seconds "
                        + "FROM event_inventories WHERE event_id = ?",
                INVENTORY_MAPPER, eventId).stream().findFirst();
    }

    public Optional<InventoryRow> findInventoryById(UUID inventoryId) {
        return jdbc.query("SELECT id, event_id, snapshot_id, hold_ttl_seconds "
                        + "FROM event_inventories WHERE id = ?",
                INVENTORY_MAPPER, inventoryId).stream().findFirst();
    }

    public boolean insertInventoryIfAbsent(UUID id, UUID eventId, UUID snapshotId,
                                           int holdTtlSeconds, OffsetDateTime initializedAt) {
        return jdbc.update("""
                INSERT INTO event_inventories
                    (id, event_id, snapshot_id, hold_ttl_seconds, initialized_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, id, eventId, snapshotId, holdTtlSeconds, initializedAt) == 1;
    }

    public void insertSector(UUID id, UUID inventoryId, UUID sourceSectorId, String name,
                             String type, int capacity) {
        jdbc.update("""
                INSERT INTO inventory_sectors
                    (id, event_inventory_id, source_sector_id, name, type, capacity, sold, reserved)
                VALUES (?, ?, ?, ?, ?, ?, 0, 0)
                """, id, inventoryId, sourceSectorId, name, type, capacity);
    }

    public void insertItem(UUID id, UUID inventoryId, UUID sectorId, UUID sourceSeatId,
                           String rowLabel, int seatNumber) {
        jdbc.update("""
                INSERT INTO inventory_items
                    (id, event_inventory_id, sector_id, source_seat_id, row_label,
                     seat_number, status)
                VALUES (?, ?, ?, ?, ?, ?, 'AVAILABLE')
                """, id, inventoryId, sectorId, sourceSeatId, rowLabel, seatNumber);
    }

    public List<SectorRow> findSectors(UUID inventoryId) {
        return jdbc.query("""
                SELECT id, source_sector_id, name, type, capacity, sold, reserved
                FROM inventory_sectors
                WHERE event_inventory_id = ?
                ORDER BY name ASC, id ASC
                """, SECTOR_MAPPER, inventoryId);
    }

    public Optional<SectorRow> findSector(UUID inventoryId, UUID sectorId) {
        return jdbc.query("""
                SELECT id, source_sector_id, name, type, capacity, sold, reserved
                FROM inventory_sectors
                WHERE event_inventory_id = ? AND id = ?
                """, SECTOR_MAPPER, inventoryId, sectorId).stream().findFirst();
    }

    public List<ItemRow> findItems(UUID sectorId) {
        return jdbc.query("""
                SELECT id, sector_id, row_label, seat_number, status
                FROM inventory_items
                WHERE sector_id = ?
                ORDER BY row_label ASC, seat_number ASC
                """, ITEM_MAPPER, sectorId);
    }

    public Optional<ItemRow> findItem(UUID sectorId, UUID itemId) {
        return jdbc.query("""
                SELECT id, sector_id, row_label, seat_number, status
                FROM inventory_items
                WHERE sector_id = ? AND id = ?
                """, ITEM_MAPPER, sectorId, itemId).stream().findFirst();
    }

    public int updateHoldTtl(UUID eventId, int ttlSeconds) {
        return jdbc.update(
                "UPDATE event_inventories SET hold_ttl_seconds = ? WHERE event_id = ?",
                ttlSeconds, eventId);
    }

    // ---------------------------------------------------------------- reservations

    public int insertReservation(UUID id, UUID orderId, UUID inventoryId, String fingerprint,
                                 OffsetDateTime expiresAt, OffsetDateTime createdAt) {
        return jdbc.update("""
                INSERT INTO reservations
                    (id, order_id, event_inventory_id, status, request_fingerprint,
                     expires_at, created_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)
                ON CONFLICT (order_id) DO NOTHING
                """, id, orderId, inventoryId, fingerprint, expiresAt, createdAt);
    }

    public void insertReservationItem(UUID id, UUID reservationId, UUID sectorId,
                                      UUID inventoryItemId, int quantity) {
        jdbc.update("""
                INSERT INTO reservation_items
                    (id, reservation_id, sector_id, inventory_item_id, quantity)
                VALUES (?, ?, ?, ?, ?)
                """, id, reservationId, sectorId, inventoryItemId, quantity);
    }

    public Optional<ReservationRow> findReservationByOrder(UUID orderId) {
        return jdbc.query(RESERVATION_SELECT + " WHERE r.order_id = ?",
                RESERVATION_MAPPER, orderId).stream().findFirst();
    }

    public Optional<ReservationRow> findReservationById(UUID reservationId) {
        return jdbc.query(RESERVATION_SELECT + " WHERE r.id = ?",
                RESERVATION_MAPPER, reservationId).stream().findFirst();
    }

    public List<ReservationItemRow> findReservationItems(UUID reservationId) {
        return jdbc.query("""
                SELECT sector_id, inventory_item_id, quantity
                FROM reservation_items
                WHERE reservation_id = ?
                ORDER BY sector_id ASC, inventory_item_id ASC NULLS LAST
                """, (rs, row) -> new ReservationItemRow(
                rs.getObject("sector_id", UUID.class),
                rs.getObject("inventory_item_id", UUID.class),
                rs.getInt("quantity")), reservationId);
    }

    public int holdSeat(UUID itemId, UUID sectorId, UUID reservationId,
                        OffsetDateTime expiresAt) {
        return jdbc.update("""
                UPDATE inventory_items
                SET status = 'HELD', reservation_id = ?, expires_at = ?
                WHERE id = ? AND sector_id = ?
                  AND (status = 'AVAILABLE'
                       OR (status = 'HELD' AND expires_at <= CURRENT_TIMESTAMP))
                """, reservationId, expiresAt, itemId, sectorId);
    }

    public int reserveGeneral(UUID sectorId, int quantity) {
        return jdbc.update("""
                UPDATE inventory_sectors
                SET reserved = reserved + ?
                WHERE id = ? AND capacity - sold - reserved >= ?
                """, quantity, sectorId, quantity);
    }

    public int countSeatItems(UUID reservationId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM reservation_items
                WHERE reservation_id = ? AND inventory_item_id IS NOT NULL
                """, Integer.class, reservationId);
        return count == null ? 0 : count;
    }

    public int countGeneralItems(UUID reservationId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM reservation_items
                WHERE reservation_id = ? AND inventory_item_id IS NULL
                """, Integer.class, reservationId);
        return count == null ? 0 : count;
    }

    public int confirmReservation(UUID reservationId) {
        return jdbc.update("""
                UPDATE reservations
                SET status = 'CONFIRMED', confirmed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP
                """, reservationId);
    }

    public int sellItems(UUID reservationId) {
        return jdbc.update("""
                UPDATE inventory_items
                SET status = 'SOLD', sold_at = CURRENT_TIMESTAMP, expires_at = NULL
                WHERE reservation_id = ? AND status = 'HELD'
                """, reservationId);
    }

    public int sellGeneral(UUID reservationId) {
        return jdbc.update("""
                UPDATE inventory_sectors s
                SET sold = s.sold + ri.quantity,
                    reserved = s.reserved - ri.quantity
                FROM reservation_items ri
                WHERE ri.reservation_id = ?
                  AND ri.inventory_item_id IS NULL
                  AND s.id = ri.sector_id
                  AND s.reserved >= ri.quantity
                """, reservationId);
    }

    public int releaseReservation(UUID reservationId) {
        return jdbc.update("""
                UPDATE reservations
                SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'ACTIVE'
                """, reservationId);
    }

    public int releaseItems(UUID reservationId) {
        return jdbc.update("""
                UPDATE inventory_items
                SET status = 'AVAILABLE', reservation_id = NULL, expires_at = NULL
                WHERE reservation_id = ? AND status = 'HELD'
                """, reservationId);
    }

    public int releaseGeneral(UUID reservationId) {
        return jdbc.update("""
                UPDATE inventory_sectors s
                SET reserved = s.reserved - ri.quantity
                FROM reservation_items ri
                WHERE ri.reservation_id = ?
                  AND ri.inventory_item_id IS NULL
                  AND s.id = ri.sector_id
                  AND s.reserved >= ri.quantity
                """, reservationId);
    }

    public int expireReservation(UUID reservationId) {
        return jdbc.update("""
                UPDATE reservations
                SET status = 'EXPIRED', expired_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP
                """, reservationId);
    }

    public List<UUID> dueReservationIds() {
        return jdbc.queryForList("""
                SELECT id FROM reservations
                WHERE status = 'ACTIVE' AND expires_at <= CURRENT_TIMESTAMP
                """, UUID.class);
    }

    public List<UUID> dueReservationIdsForEvent(UUID inventoryId) {
        return jdbc.queryForList("""
                SELECT id FROM reservations
                WHERE event_inventory_id = ?
                  AND status = 'ACTIVE'
                  AND expires_at <= CURRENT_TIMESTAMP
                """, UUID.class, inventoryId);
    }

public int cancelInventory(UUID eventId) {
    return jdbc.update(
            "UPDATE event_inventories SET cancelled_at = COALESCE(cancelled_at, CURRENT_TIMESTAMP) "
                    + "WHERE event_id = ?",
            eventId);
}

public boolean isCancelled(UUID eventId) {
    Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM event_inventories WHERE event_id = ? AND cancelled_at IS NOT NULL",
            Integer.class, eventId);
    return count != null && count > 0;
}


}
