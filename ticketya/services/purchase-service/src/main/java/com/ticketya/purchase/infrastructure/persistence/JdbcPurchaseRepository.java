package com.ticketya.purchase.infrastructure.persistence;

import com.ticketya.purchase.domain.model.OrderItem;
import com.ticketya.purchase.domain.model.OrderRecord;
import com.ticketya.purchase.domain.model.OrderTicket;
import com.ticketya.purchase.domain.model.OrderUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acceso a datos de compra con JDBC sobre purchase_db. Sin claves foráneas ajenas. */
@Repository
public class JdbcPurchaseRepository {

    private static final String ORDER_COLUMNS =
            "id, customer_id, event_id, event_name, event_starts_at, venue_name, venue_city, "
            + "currency, total_amount, status, payment_id, payment_status, payment_method, "
            + "payment_created_at, payment_updated_at, idempotency_key, request_fingerprint, "
            + "reservation_id, reservation_expires_at, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public JdbcPurchaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------- orden

    public Optional<OrderRecord> findOrder(UUID orderId) {
        List<OrderRecord> rows = jdbc.query(
                "SELECT " + ORDER_COLUMNS + " FROM purchase_orders WHERE id = ?",
                (rs, rowNum) -> mapOrder(rs), orderId);
        return rows.stream().findFirst();
    }

    public Optional<OrderRecord> findOrderByKeyAndCustomer(UUID customerId, String idempotencyKey) {
        List<OrderRecord> rows = jdbc.query(
                "SELECT " + ORDER_COLUMNS + " FROM purchase_orders WHERE customer_id = ? AND idempotency_key = ?",
                (rs, rowNum) -> mapOrder(rs), customerId, idempotencyKey);
        return rows.stream().findFirst();
    }

    public void insertOrder(OrderRecord order) {
        jdbc.update(
                "INSERT INTO purchase_orders "
                        + "(id, customer_id, event_id, event_name, event_starts_at, venue_name, venue_city, "
                        + "currency, total_amount, status, payment_id, payment_status, payment_method, "
                        + "payment_created_at, payment_updated_at, idempotency_key, request_fingerprint, "
                        + "reservation_id, reservation_expires_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                order.id(), order.customerId(), order.eventId(), order.eventName(),
                order.eventStartsAt(), order.venueName(), order.venueCity(), order.currency(),
                order.totalAmount(), order.status(), order.paymentId(), order.paymentStatus(),
                order.paymentMethod(), order.paymentCreatedAt(), order.paymentUpdatedAt(),
                order.idempotencyKey(), order.requestFingerprint(), order.reservationId(),
                order.reservationExpiresAt(), order.createdAt(), order.updatedAt());
    }

    public void updateOrderReservation(UUID orderId, UUID reservationId, OffsetDateTime expiresAt) {
        jdbc.update(
                "UPDATE purchase_orders SET reservation_id = ?, reservation_expires_at = ?, updated_at = now() WHERE id = ?",
                reservationId, expiresAt, orderId);
    }

    public void updateOrderPayment(UUID orderId, UUID paymentId, String paymentStatus,
                                   String currency, BigDecimal amount, String method,
                                   OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        jdbc.update(
                "UPDATE purchase_orders "
                        + "SET payment_id = ?, payment_status = ?, payment_method = ?, "
                        + "payment_created_at = ?, payment_updated_at = ?, updated_at = now() "
                        + "WHERE id = ?",
                paymentId, paymentStatus, method, createdAt, updatedAt, orderId);
    }

    public void updateOrderStatus(UUID orderId, String status, String paymentStatus,
                                  OffsetDateTime reservationExpiresAt) {
        jdbc.update(
                "UPDATE purchase_orders SET status = ?, payment_status = ?, reservation_expires_at = ?, updated_at = now() WHERE id = ?",
                status, paymentStatus, reservationExpiresAt, orderId);
    }

    // ------------------------------------------------------------- ítems y unidades

    public void insertOrderItem(OrderItem item) {
        jdbc.update(
                "INSERT INTO purchase_order_items "
                        + "(id, order_id, inventory_sector_id, source_sector_id, sector_name, sector_type, "
                        + "quantity, unit_price, subtotal) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                item.id(), item.orderId(), item.inventorySectorId(), item.sourceSectorId(),
                item.sectorName(), item.sectorType(), item.quantity(), item.unitPrice(),
                item.subtotal());
    }

    public void insertOrderUnit(OrderUnit unit) {
        jdbc.update(
                "INSERT INTO order_item_units "
                        + "(id, order_item_id, unit_number, inventory_seat_id, source_seat_id, row_label, seat_number) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                unit.id(), unit.orderItemId(), unit.unitNumber(), unit.inventorySeatId(),
                unit.sourceSeatId(), unit.rowLabel(), unit.seatNumber());
    }

    public void insertOrderTicket(OrderTicket ticket) {
        jdbc.update(
                "INSERT INTO order_tickets "
                        + "(id, order_id, order_item_id, ticket_id, unit_number, status, qr_payload, "
                        + "qr_png_base64, row_label, seat_number) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (order_id, order_item_id, unit_number) DO NOTHING",
                ticket.id(), ticket.orderId(), ticket.orderItemId(), ticket.ticketId(),
                ticket.unitNumber(), ticket.status(), ticket.qrPayload(), ticket.qrPngBase64(),
                ticket.rowLabel(), ticket.seatNumber());
    }

    public List<OrderItem> findItemsByOrder(UUID orderId) {
        return jdbc.query(
                "SELECT id, order_id, inventory_sector_id, source_sector_id, sector_name, sector_type, "
                        + "quantity, unit_price, subtotal FROM purchase_order_items WHERE order_id = ? ORDER BY id",
                (rs, rowNum) -> new OrderItem(
                        rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getObject("inventory_sector_id", UUID.class),
                        rs.getObject("source_sector_id", UUID.class),
                        rs.getString("sector_name"),
                        rs.getString("sector_type"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("subtotal")),
                orderId);
    }

    public List<OrderUnit> findUnitsByItem(UUID orderItemId) {
        return jdbc.query(
                "SELECT id, order_item_id, unit_number, inventory_seat_id, source_seat_id, row_label, seat_number "
                        + "FROM order_item_units WHERE order_item_id = ? ORDER BY unit_number",
                (rs, rowNum) -> new OrderUnit(
                        rs.getObject("id", UUID.class),
                        rs.getObject("order_item_id", UUID.class),
                        rs.getInt("unit_number"),
                        rs.getObject("inventory_seat_id", UUID.class),
                        rs.getObject("source_seat_id", UUID.class),
                        rs.getString("row_label"),
                        rs.getObject("seat_number", Integer.class)),
                orderItemId);
    }

    public List<OrderTicket> findTicketsByItem(UUID orderItemId) {
        return jdbc.query(
                "SELECT id, order_id, order_item_id, ticket_id, unit_number, status, qr_payload, "
                        + "qr_png_base64, row_label, seat_number FROM order_tickets WHERE order_item_id = ? ORDER BY unit_number",
                (rs, rowNum) -> new OrderTicket(
                        rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getObject("order_item_id", UUID.class),
                        rs.getObject("ticket_id", UUID.class),
                        rs.getInt("unit_number"),
                        rs.getString("status"),
                        rs.getString("qr_payload"),
                        rs.getString("qr_png_base64"),
                        rs.getString("row_label"),
                        rs.getObject("seat_number", Integer.class)),
                orderItemId);
    }

    // ------------------------------------------------------------- límite por cliente y evento

    public void commitLimit(UUID customerId, UUID eventId, int units) {
        jdbc.update(
                "UPDATE customer_event_limits "
                        + "SET pending_units = GREATEST(pending_units - ?, 0), "
                        + "committed_units = committed_units + ? "
                        + "WHERE customer_id = ? AND event_id = ?",
                units, units, customerId, eventId);
    }

    public void releaseLimit(UUID customerId, UUID eventId, int units) {
        jdbc.update(
                "UPDATE customer_event_limits SET pending_units = GREATEST(pending_units - ?, 0) "
                        + "WHERE customer_id = ? AND event_id = ?",
                units, customerId, eventId);
        jdbc.update(
                "DELETE FROM customer_event_limits WHERE customer_id = ? AND event_id = ? "
                        + "AND pending_units = 0 AND committed_units = 0",
                customerId, eventId);
    }

    // ------------------------------------------------------------- listado paginado

    public List<OrderRecord> listOrders(UUID customerId, boolean admin, String status,
                                        int page, int size) {
        ArrayList<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT " + ORDER_COLUMNS + " FROM purchase_orders WHERE 1 = 1");
        if (!admin) {
            sql.append(" AND customer_id = ?");
            params.add(customerId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);
        return jdbc.query(sql.toString(), params.toArray(), (rs, rowNum) -> mapOrder(rs));
    }

    public int countOrders(UUID customerId, boolean admin, String status) {
        ArrayList<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM purchase_orders WHERE 1 = 1");
        if (!admin) {
            sql.append(" AND customer_id = ?");
            params.add(customerId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        return jdbc.queryForObject(sql.toString(), params.toArray(), Integer.class);
    }

    // ------------------------------------------------------------- mapeo

    private OrderRecord mapOrder(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrderRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("customer_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_name"),
                rs.getObject("event_starts_at", OffsetDateTime.class),
                rs.getString("venue_name"),
                rs.getString("venue_city"),
                rs.getString("currency"),
                rs.getBigDecimal("total_amount"),
                rs.getString("status"),
                rs.getObject("payment_id", UUID.class),
                rs.getString("payment_status"),
                rs.getString("payment_method"),
                rs.getObject("payment_created_at", OffsetDateTime.class),
                rs.getObject("payment_updated_at", OffsetDateTime.class),
                rs.getString("idempotency_key"),
                rs.getString("request_fingerprint"),
                rs.getObject("reservation_id", UUID.class),
                rs.getObject("reservation_expires_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
