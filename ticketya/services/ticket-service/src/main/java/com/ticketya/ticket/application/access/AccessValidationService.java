package com.ticketya.ticket.application.access;

import com.ticketya.ticket.api.access.AccessScanPage;
import com.ticketya.ticket.api.access.AccessScanView;
import com.ticketya.ticket.api.access.AccessValidationResponse;
import com.ticketya.ticket.application.TicketQrSigner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * US-F8: validación de entradas en el acceso al evento.
 *
 * <p>Mecanismo contra el doble ingreso: un único {@code UPDATE ... WHERE
 * status = 'ISSUED'} condicional. PostgreSQL serializa las escrituras sobre
 * la misma fila, de modo que ante escaneos simultáneos (incluso desde varias
 * instancias del servicio) exactamente uno afecta la fila y el resto ve
 * {@code USED}. El índice único parcial de {@code access_scans} es una segunda
 * barrera: a lo sumo un registro {@code ADMITTED} por entrada.
 */
@Service
public class AccessValidationService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final TicketQrSigner signer;

    public AccessValidationService(JdbcTemplate jdbc, TransactionTemplate transactions, TicketQrSigner signer) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.signer = signer;
    }

    public AccessValidationResponse validate(UUID eventId, String qrPayload, String gate, UUID staffId,
                                             String correlationId) {
        String normalizedGate = gate == null || gate.isBlank() ? null : gate.trim();
        Optional<UUID> verified = signer.verify(qrPayload);
        if (verified.isEmpty()) {
            recordScan(eventId, null, staffId, normalizedGate, AccessResult.UNKNOWN_TICKET, correlationId);
            throw new AccessRejectedException(AccessResult.UNKNOWN_TICKET, null, null, null);
        }
        UUID ticketId = verified.get();
        String qrHash = signer.hash(qrPayload.trim());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);

        Boolean admitted = transactions.execute(status -> {
            int updated = jdbc.update("""
                    UPDATE tickets SET status = 'USED', used_at = ?, used_by = ?, used_gate = ?
                    WHERE id = ? AND event_id = ? AND qr_hash = ? AND status = 'ISSUED'
                    """, now, staffId, normalizedGate, ticketId, eventId, qrHash);
            if (updated == 1) {
                insertScan(eventId, ticketId, staffId, normalizedGate, AccessResult.ADMITTED, correlationId);
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(admitted)) {
            return admittedView(ticketId);
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT event_id, qr_hash, status, used_at, used_gate FROM tickets WHERE id = ?", ticketId);
        AccessResult reason;
        OffsetDateTime usedAt = null;
        String usedGate = null;
        if (rows.isEmpty() || !qrHash.equals(rows.get(0).get("qr_hash"))) {
            reason = AccessResult.UNKNOWN_TICKET;
        } else if (!eventId.equals(rows.get(0).get("event_id"))) {
            reason = AccessResult.WRONG_EVENT;
        } else if ("REVOKED".equals(rows.get(0).get("status"))) {
            reason = AccessResult.REVOKED;
        } else {
            reason = AccessResult.ALREADY_USED;
            usedAt = jdbc.queryForObject("SELECT used_at FROM tickets WHERE id = ?", OffsetDateTime.class, ticketId);
            usedGate = (String) rows.get(0).get("used_gate");
        }
        UUID knownTicket = reason == AccessResult.UNKNOWN_TICKET ? null : ticketId;
        recordScan(eventId, knownTicket, staffId, normalizedGate, reason, correlationId);
        throw new AccessRejectedException(reason, knownTicket, usedAt, usedGate);
    }

    public AccessScanPage scans(UUID eventId, int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and 200");
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AccessResult result : AccessResult.values()) {
            counts.put(result.name(), 0L);
        }
        jdbc.query("SELECT result, COUNT(*) AS total FROM access_scans WHERE event_id = ? GROUP BY result",
                rs -> {
                    counts.put(rs.getString("result"), rs.getLong("total"));
                }, eventId);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long admitted = counts.get(AccessResult.ADMITTED.name());
        List<AccessScanView> items = jdbc.query("""
                SELECT id, ticket_id, staff_id, gate, result, scanned_at, correlation_id
                FROM access_scans WHERE event_id = ?
                ORDER BY scanned_at DESC, id LIMIT ? OFFSET ?
                """, (rs, n) -> new AccessScanView(
                rs.getObject("id", UUID.class),
                rs.getObject("ticket_id", UUID.class),
                rs.getObject("staff_id", UUID.class),
                rs.getString("gate"),
                rs.getString("result"),
                rs.getObject("scanned_at", OffsetDateTime.class),
                rs.getString("correlation_id")), eventId, size, (long) page * size);
        return new AccessScanPage(eventId, admitted, total - admitted, counts, page, size, total, items);
    }

    private AccessValidationResponse admittedView(UUID ticketId) {
        return jdbc.queryForObject("""
                SELECT id, event_id, event_name, sector_name, row_label, seat_number, status,
                       used_at, used_gate, used_by
                FROM tickets WHERE id = ?
                """, (rs, n) -> new AccessValidationResponse(
                AccessResult.ADMITTED.name(),
                rs.getObject("id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_name"),
                rs.getString("sector_name"),
                rs.getString("row_label"),
                (Integer) rs.getObject("seat_number"),
                rs.getString("status"),
                rs.getObject("used_at", OffsetDateTime.class),
                rs.getString("used_gate"),
                rs.getObject("used_by", UUID.class)), ticketId);
    }

    private void recordScan(UUID eventId, UUID ticketId, UUID staffId, String gate, AccessResult result,
                            String correlationId) {
        insertScan(eventId, ticketId, staffId, gate, result, correlationId);
    }

    private void insertScan(UUID eventId, UUID ticketId, UUID staffId, String gate, AccessResult result,
                            String correlationId) {
        jdbc.update("""
                INSERT INTO access_scans (id, event_id, ticket_id, staff_id, gate, result, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), eventId, ticketId, staffId, gate, result.name(), correlationId);
    }
}
