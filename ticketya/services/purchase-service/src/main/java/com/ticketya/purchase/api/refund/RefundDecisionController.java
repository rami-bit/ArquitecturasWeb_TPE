package com.ticketya.purchase.api.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * US-F7.2: un administrador aprueba o rechaza una solicitud de devolución. Al
 * aprobar, Purchase revoca el ticket, reintegra el pago y libera la unidad
 * vendida, persistiendo el progreso para poder reintentar cada paso fallido.
 */
@RestController
@RequestMapping("/api/refunds")
public class RefundDecisionController {
    private static final Logger log = LoggerFactory.getLogger(RefundDecisionController.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    private final RestClient http = RestClient.builder().build();
    private final String ticketUrl;
    private final String paymentUrl;
    private final String inventoryUrl;

    public RefundDecisionController(JdbcTemplate jdbc, TransactionTemplate transactions,
            ObjectMapper mapper,
            @Value("${purchase.ticket.url:http://localhost:8083}") String ticketUrl,
            @Value("${purchase.payment.url:http://localhost:8085}") String paymentUrl,
            @Value("${purchase.inventory.url:http://localhost:8082}") String inventoryUrl) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mapper = mapper;
        this.ticketUrl = ticketUrl;
        this.paymentUrl = paymentUrl;
        this.inventoryUrl = inventoryUrl;
    }

    @PostMapping("/{refundId}/decision")
    public ResponseEntity<RefundRequestController.RefundView> decide(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable("refundId") UUID refundId, @RequestBody DecisionRequest request) {
        if (!"ADMIN".equals(role)) throw problem(HttpStatus.FORBIDDEN, "ADMIN role required");
        if (request == null || !RefundPolicy.isValidDecision(request.decision())) {
            throw problem(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        }
        String decision = request.decision();
        transactions.executeWithoutResult(tx -> {
            RefundRow current = row(refundId);
            if (!RefundPolicy.canSetDecision(current.decision(), decision)) {
                throw problem(HttpStatus.CONFLICT, "Refund decision cannot be changed");
            }
            if (current.decision() == null) {
                int changed = jdbc.update("""
                        UPDATE refund_requests SET decision=?, status=?
                        WHERE id=? AND decision IS NULL
                        """, decision, "APPROVED".equals(decision) ? "APPROVED" : "REJECTED", refundId);
                if (changed == 0 && !decision.equals(row(refundId).decision())) {
                    throw problem(HttpStatus.CONFLICT, "Refund decision cannot be changed");
                }
            }
        });
        if ("REJECTED".equals(decision)) return ResponseEntity.ok(view(refundId));
        String trace = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId;
        boolean completed = resumeApproved(refundId, trace);
        return ResponseEntity.status(completed ? HttpStatus.OK : HttpStatus.ACCEPTED).body(view(refundId));
    }

    public boolean resumeApproved(UUID refundId, String trace) {
        try {
            RefundRow refund = row(refundId);
            if (!"APPROVED".equals(refund.decision())) return false;
            if ("COMPLETED".equals(refund.progress())) return true;
            if ("NOT_STARTED".equals(refund.progress())) {
                JsonNode revoked = post(ticketUrl + "/internal/tickets/" + refund.ticketId() + "/revoke",
                        trace, null);
                if (!"REVOKED".equals(revoked.path("status").asText())) {
                    throw new IllegalStateException("Ticket was not revoked");
                }
                advance(refundId, "NOT_STARTED", "TICKET_REVOKED");
            }
            refund = row(refundId);
            if ("TICKET_REVOKED".equals(refund.progress())) {
                if (refund.paymentId() == null) throw new IllegalStateException("Payment reference unavailable");
                Map<String, Object> paymentRequest = new LinkedHashMap<>();
                paymentRequest.put("amount", refund.amount());
                paymentRequest.put("idempotencyKey", refundId.toString());
                JsonNode paidBack = post(paymentUrl + "/internal/payments/" + refund.paymentId() + "/refunds",
                        trace, paymentRequest);
                if (!"REFUNDED".equals(paidBack.path("status").asText())) {
                    throw new IllegalStateException("Refund was not confirmed");
                }
                advance(refundId, "TICKET_REVOKED", "PAYMENT_REFUNDED");
            }
            refund = row(refundId);
            if ("PAYMENT_REFUNDED".equals(refund.progress())) {
                if (refund.reservationId() == null || refund.unitNumber() < 1) {
                    throw new IllegalStateException("Sold unit reference unavailable");
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("sectorId", refund.sectorId());
                body.put("seatId", refund.seatId());
                body.put("unitNumber", refund.unitNumber());
                JsonNode released = post(inventoryUrl + "/internal/reservations/" + refund.reservationId()
                        + "/sold-units/release", trace, body);
                if (!refund.reservationId().toString().equals(released.path("reservationId").asText())) {
                    throw new IllegalStateException("Sold unit release was not confirmed");
                }
                jdbc.update("""
                        UPDATE refund_requests SET progress='COMPLETED',status='COMPLETED'
                        WHERE id=? AND progress='PAYMENT_REFUNDED'
                        """, refundId);
            }
            return "COMPLETED".equals(row(refundId).progress());
        } catch (Exception ex) {
            log.warn("Refund processing paused refundId={} correlationId={}", refundId, trace, ex);
            return false;
        }
    }

    private void advance(UUID id, String from, String to) {
        jdbc.update("UPDATE refund_requests SET progress=? WHERE id=? AND progress=?", to, id, from);
    }

    private JsonNode post(String url, String trace, Object body) throws Exception {
        var request = http.post().uri(url).header("X-Correlation-Id", trace);
        String response = body == null ? request.retrieve().body(String.class)
                : request.body(body).retrieve().body(String.class);
        return mapper.readTree(response);
    }

    private RefundRow row(UUID id) {
        List<RefundRow> rows = jdbc.query("""
                SELECT r.ticket_id, r.decision, r.progress, r.amount, o.payment_id, o.reservation_id,
                       i.inventory_sector_id AS sector_id,
                       u.inventory_seat_id AS seat_id,
                       t.unit_number
                FROM refund_requests r
                JOIN purchase_orders o ON o.id = r.order_id
                JOIN purchase_order_items i ON i.id = r.order_item_id
                JOIN order_tickets t ON t.order_item_id = i.id AND t.ticket_id = r.ticket_id
                LEFT JOIN order_item_units u
                       ON u.order_item_id = i.id AND u.unit_number = t.unit_number
                WHERE r.id=?
                """, (rs, n) -> new RefundRow(rs.getObject("ticket_id", UUID.class),
                rs.getString("decision"), rs.getString("progress"), rs.getBigDecimal("amount"),
                rs.getObject("payment_id", UUID.class), rs.getObject("reservation_id", UUID.class),
                rs.getObject("sector_id", UUID.class), rs.getObject("seat_id", UUID.class),
                rs.getInt("unit_number")), id);
        if (rows.isEmpty()) throw problem(HttpStatus.NOT_FOUND, "Refund request not found");
        return rows.get(0);
    }

    private RefundRequestController.RefundView view(UUID id) {
        return jdbc.queryForObject("""
                SELECT id,ticket_id,order_id,event_id,amount,status,decision,progress,created_at
                FROM refund_requests WHERE id=?
                """, (rs, n) -> map(rs), id);
    }

    private RefundRequestController.RefundView map(ResultSet rs) throws SQLException {
        return new RefundRequestController.RefundView(rs.getObject("id", UUID.class),
                rs.getObject("ticket_id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getObject("event_id", UUID.class), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getString("decision"), rs.getString("progress"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private ResponseStatusException problem(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    public record DecisionRequest(String decision) {
    }

    private record RefundRow(UUID ticketId, String decision, String progress, BigDecimal amount,
            UUID paymentId, UUID reservationId, UUID sectorId, UUID seatId, int unitNumber) {
    }
}
