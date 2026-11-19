package com.ticketya.purchase.api.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.purchase.infrastructure.audit.AuditEvent;
import com.ticketya.purchase.infrastructure.audit.AuditPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * US-F7.1: el titular de un ticket pagado solicita su devolución hasta antes de
 * las 72 horas previas al evento. La solicitud es idempotente por ticket.
 */
@RestController
@RequestMapping("/api/refunds")
public class RefundRequestController {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RestClient http;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String catalogUrl;
    private final AuditPublisher audit;

    public RefundRequestController(JdbcTemplate jdbc, TransactionTemplate transactions,
            ObjectMapper mapper, Clock clock, String catalogUrl) {
        this(jdbc, transactions, mapper, clock, catalogUrl, AuditPublisher.localOnly("purchase-service", mapper));
    }

    @Autowired
    public RefundRequestController(JdbcTemplate jdbc, TransactionTemplate transactions,
            ObjectMapper mapper, Clock clock,
            @Value("${purchase.catalog.url:http://localhost:8081}") String catalogUrl,
            AuditPublisher audit) {
        this.jdbc = jdbc; this.transactions = transactions; this.mapper = mapper;
        this.clock = clock; this.catalogUrl = catalogUrl;
        this.http = RestClient.builder().build();
        this.audit = audit;
    }

    @PostMapping("/tickets/{ticketId}")
    public ResponseEntity<RefundView> request(@RequestHeader("X-User-Id") UUID customerId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable("ticketId") UUID ticketId) {
        try {
            ResponseEntity<RefundView> response = requestRefund(customerId, correlationId, ticketId);
            RefundView view = response.getBody();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("ticketId", ticketId.toString());
            details.put("eventId", view.eventId().toString());
            details.put("amount", view.amount());
            details.put("replay", response.getStatusCode().value() == 200);
            audit.publish(AuditEvent.of(correlationId, "REFUND_REQUEST", view.status(), customerId.toString(),
                    "CLIENT", "REFUND", view.id(), details));
            return response;
        } catch (ResponseStatusException rejected) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("ticketId", ticketId.toString());
            details.put("httpStatus", rejected.getStatusCode().value());
            details.put("detail", rejected.getReason());
            audit.publish(AuditEvent.of(correlationId, "REFUND_REQUEST", "FAILED", customerId.toString(),
                    "CLIENT", "TICKET", ticketId, details));
            throw rejected;
        }
    }

    private ResponseEntity<RefundView> requestRefund(UUID customerId, String correlationId, UUID ticketId) {
        TicketOrder ticket = ownedTicket(customerId, ticketId);
        RefundView prior = byTicket(ticketId);
        if (prior != null) return ResponseEntity.ok(prior);
        String trace = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId;
        OffsetDateTime startsAt;
        try {
            String json = http.get().uri(catalogUrl + "/api/events/" + ticket.eventId())
                    .header("X-Correlation-Id", trace).retrieve().body(String.class);
            JsonNode event = mapper.readTree(json);
            startsAt = OffsetDateTime.parse(event.path("startsAt").asText());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Event date unavailable");
        }
        if (!RefundPolicy.isWithinRequestWindow(startsAt, clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund window has closed");
        }
        UUID id = UUID.randomUUID();
        try {
            transactions.executeWithoutResult(status -> jdbc.update("""
                    INSERT INTO refund_requests(id,ticket_id,order_id,order_item_id,customer_id,event_id,amount,source)
                    VALUES (?,?,?,?,?,?,?,'CUSTOMER') ON CONFLICT (ticket_id,source) DO NOTHING
                    """, id, ticketId, ticket.orderId(), ticket.orderItemId(), customerId,
                    ticket.eventId(), ticket.price()));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund request could not be created");
        }
        RefundView view = byTicket(ticketId);
        return ResponseEntity.status(view.id().equals(id) ? HttpStatus.CREATED : HttpStatus.OK).body(view);
    }

    @GetMapping("/{refundId}")
    public RefundView get(@RequestHeader("X-User-Id") UUID customerId, @PathVariable("refundId") UUID refundId) {
        List<RefundView> found = jdbc.query("""
                SELECT id,ticket_id,order_id,event_id,amount,status,decision,progress,created_at
                FROM refund_requests WHERE id=? AND customer_id=?
                """, (rs, n) -> map(rs), refundId, customerId);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund request not found");
        }
        return found.get(0);
    }

    private RefundView byTicket(UUID ticketId) {
        List<RefundView> found = jdbc.query("""
                SELECT id,ticket_id,order_id,event_id,amount,status,decision,progress,created_at
                FROM refund_requests WHERE ticket_id=? AND source='CUSTOMER'
                """, (rs, n) -> map(rs), ticketId);
        return found.isEmpty() ? null : found.get(0);
    }

    private RefundView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RefundView(rs.getObject("id", UUID.class), rs.getObject("ticket_id", UUID.class),
                rs.getObject("order_id", UUID.class), rs.getObject("event_id", UUID.class),
                rs.getBigDecimal("amount"), rs.getString("status"), rs.getString("decision"),
                rs.getString("progress"), rs.getObject("created_at", OffsetDateTime.class));
    }

    /** Ticket emitido de una orden confirmada del propio comprador. */
    private TicketOrder ownedTicket(UUID customerId, UUID ticketId) {
        List<TicketOrder> found = jdbc.query("""
                SELECT o.id AS order_id, i.id AS item_id, o.event_id, i.unit_price
                FROM purchase_orders o
                JOIN purchase_order_items i ON i.order_id = o.id
                JOIN order_tickets t ON t.order_item_id = i.id
                WHERE o.customer_id = ? AND o.status = 'CONFIRMED' AND t.ticket_id = ?
                """, (rs, n) -> new TicketOrder(rs.getObject("order_id", UUID.class),
                rs.getObject("item_id", UUID.class), rs.getObject("event_id", UUID.class),
                rs.getBigDecimal("unit_price")), customerId, ticketId);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found");
        }
        return found.get(0);
    }

    record TicketOrder(UUID orderId, UUID orderItemId, UUID eventId, BigDecimal price) {
    }

    public record RefundView(UUID id, UUID ticketId, UUID orderId, UUID eventId, BigDecimal amount,
            String status, String decision, String progress, OffsetDateTime createdAt) {
    }
}
