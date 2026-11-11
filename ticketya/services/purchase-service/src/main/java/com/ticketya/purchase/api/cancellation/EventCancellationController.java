package com.ticketya.purchase.api.cancellation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.purchase.api.refund.RefundDecisionController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * US-F7.3: cancelación de un evento por parte de un ADMIN. Catalog marca el
 * evento como CANCELLED y Purchase procesa por lotes los tickets pagados:
 * revocación, reintegro y liberación de la unidad vendida.
 */
@RestController
@RequestMapping("/api/events/{eventId}/cancellation")
public class EventCancellationController {
    private static final int PAGE_SIZE = 20;
    private final JdbcTemplate jdbc;
    private final RefundDecisionController refunds;
    private final ObjectMapper mapper;
    private final RestClient http = RestClient.builder().build();
    private final String catalogUrl;

    public EventCancellationController(JdbcTemplate jdbc, RefundDecisionController refunds,
            ObjectMapper mapper,
            @Value("${purchase.catalog.url:http://localhost:8081}") String catalogUrl) {
        this.jdbc = jdbc;
        this.refunds = refunds;
        this.mapper = mapper;
        this.catalogUrl = catalogUrl;
    }

    @PostMapping
    public ResponseEntity<CancellationView> start(@PathVariable("eventId") UUID eventId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader("X-User-Id") UUID adminId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        requireAdmin(role);
        String trace = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId;
        cancelInCatalog(eventId, adminId, trace);
        jdbc.update("INSERT INTO event_cancellations(event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
                eventId);
        UUID cursor = jdbc.queryForObject(
                "SELECT last_ticket_id FROM event_cancellations WHERE event_id=?", UUID.class, eventId);
        List<SoldTicket> page = jdbc.query("""
                SELECT t.ticket_id, o.id AS order_id, i.id AS item_id, o.customer_id, i.unit_price
                FROM purchase_orders o
                JOIN purchase_order_items i ON i.order_id = o.id
                JOIN order_tickets t ON t.order_item_id = i.id
                WHERE o.event_id = ? AND o.status = 'CONFIRMED'
                  AND t.ticket_id > COALESCE(?::uuid, '00000000-0000-0000-0000-000000000000'::uuid)
                ORDER BY t.ticket_id
                LIMIT ?
                """, (rs, n) -> new SoldTicket(rs.getObject("ticket_id", UUID.class),
                rs.getObject("order_id", UUID.class), rs.getObject("item_id", UUID.class),
                rs.getObject("customer_id", UUID.class), rs.getBigDecimal("unit_price")),
                eventId, cursor == null ? null : cursor.toString(), PAGE_SIZE);
        for (SoldTicket ticket : page) {
            UUID refundId = approvedRefund(eventId, ticket);
            refunds.resumeApproved(refundId, trace);
        }
        UUID next = page.size() == PAGE_SIZE ? page.get(page.size() - 1).ticketId() : null;
        jdbc.update("UPDATE event_cancellations SET last_ticket_id=? WHERE event_id=?", next, eventId);
        CancellationView view = progress(eventId);
        return ResponseEntity.status("COMPLETED".equals(view.status()) ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(view);
    }

    @GetMapping
    public CancellationView get(@PathVariable("eventId") UUID eventId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_cancellations WHERE event_id=?", Integer.class, eventId);
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cancellation not found");
        }
        return progress(eventId);
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN role required");
        }
    }

    private void cancelInCatalog(UUID eventId, UUID adminId, String trace) {
        try {
            String body = http.post().uri(catalogUrl + "/api/management/events/" + eventId + "/cancel")
                    .header("X-User-Id", adminId.toString()).header("X-User-Role", "ADMIN")
                    .header("X-Correlation-Id", trace).retrieve().body(String.class);
            JsonNode event = mapper.readTree(body);
            if (!"CANCELLED".equals(event.path("status").asText())) {
                throw new IllegalStateException("Catalog did not confirm cancellation");
            }
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found", ex);
            }
            if (ex.getStatusCode().value() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Event cannot be cancelled", ex);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Event cancellation unavailable", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Event cancellation unavailable", ex);
        }
    }

    private UUID approvedRefund(UUID eventId, SoldTicket ticket) {
        List<RefundState> existing = jdbc.query("""
                SELECT id,decision,source FROM refund_requests WHERE ticket_id=?
                ORDER BY CASE WHEN decision='APPROVED' THEN 0
                              WHEN source='CANCELLATION' THEN 1
                              WHEN decision IS NULL THEN 2 ELSE 3 END
                LIMIT 1
                """, (rs, n) -> new RefundState(rs.getObject("id", UUID.class),
                rs.getString("decision"), rs.getString("source")), ticket.ticketId());
        if (!existing.isEmpty()) {
            RefundState state = existing.get(0);
            if ("APPROVED".equals(state.decision())) return state.id();
            if (state.decision() == null) {
                int changed = jdbc.update(
                        "UPDATE refund_requests SET decision='APPROVED',status='APPROVED' WHERE id=? AND decision IS NULL",
                        state.id());
                if (changed == 1) return state.id();
                // Un administrador pudo rechazar la solicitud del cliente en paralelo.
                return approvedRefund(eventId, ticket);
            }
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO refund_requests(id,ticket_id,order_id,order_item_id,customer_id,event_id,
                                            amount,status,decision,source)
                VALUES (?,?,?,?,?,?,?,'APPROVED','APPROVED','CANCELLATION')
                ON CONFLICT (ticket_id,source) DO NOTHING
                """, id, ticket.ticketId(), ticket.orderId(), ticket.itemId(), ticket.customerId(),
                eventId, ticket.amount());
        return jdbc.queryForObject(
                "SELECT id FROM refund_requests WHERE ticket_id=? AND source='CANCELLATION'",
                UUID.class, ticket.ticketId());
    }

    private CancellationView progress(UUID eventId) {
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM purchase_orders o
                JOIN purchase_order_items i ON i.order_id = o.id
                JOIN order_tickets t ON t.order_item_id = i.id
                WHERE o.event_id = ? AND o.status = 'CONFIRMED'
                """, Integer.class, eventId);
        Integer completed = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT r.ticket_id) FROM refund_requests r
                WHERE r.event_id=? AND r.progress='COMPLETED'
                """, Integer.class, eventId);
        int all = total == null ? 0 : total;
        int done = completed == null ? 0 : completed;
        int pending = Math.max(0, all - done);
        return new CancellationView(eventId, pending == 0 ? "COMPLETED" : "PROCESSING", all, done, pending);
    }

    public record CancellationView(UUID eventId, String status, int totalTickets,
                                   int completedTickets, int pendingTickets) {
    }

    private record SoldTicket(UUID ticketId, UUID orderId, UUID itemId, UUID customerId,
                              BigDecimal amount) {
    }

    private record RefundState(UUID id, String decision, String source) {
    }
}
