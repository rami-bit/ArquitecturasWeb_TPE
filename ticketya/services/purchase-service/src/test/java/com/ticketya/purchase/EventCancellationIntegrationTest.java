package com.ticketya.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticketya.purchase.api.cancellation.EventCancellationController;
import com.ticketya.purchase.api.refund.RefundDecisionController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
class EventCancellationIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");
    private final UUID event = UUID.randomUUID();
    private final UUID customer = UUID.randomUUID();
    private final UUID order = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private final UUID rejected = UUID.randomUUID();
    private final UUID payment = UUID.randomUUID();
    private final UUID reservation = UUID.randomUUID();
    private final AtomicInteger revokes = new AtomicInteger();
    private final AtomicInteger repayments = new AtomicInteger();
    private final AtomicInteger releases = new AtomicInteger();
    private JdbcTemplate jdbc;
    private HttpServer server;
    private MockMvc mvc;

    @BeforeEach void setup() throws IOException {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM event_cancellations");
        jdbc.update("DELETE FROM refund_requests");
        jdbc.update("DELETE FROM order_tickets");
        jdbc.update("DELETE FROM order_item_units");
        jdbc.update("DELETE FROM purchase_order_items");
        jdbc.update("DELETE FROM purchase_orders");
        jdbc.update("""
                INSERT INTO purchase_orders(id,customer_id,event_id,event_name,event_starts_at,venue_name,venue_city,
                    payment_method,payment_status,status,idempotency_key,request_fingerprint,payment_id,reservation_id,total_amount)
                VALUES (?,?,?,'Show','2027-01-01T20:00:00Z','Hall','Buenos Aires','CARD','APPROVED','CONFIRMED',?,
                    'fingerprint',?,?,2500)
                """, order, customer, event, UUID.randomUUID().toString(), payment, reservation);
        jdbc.update("""
                INSERT INTO purchase_order_items(id,order_id,inventory_sector_id,source_sector_id,
                    sector_name,sector_type,unit_price,quantity,subtotal)
                VALUES (?,?,?,?,'General','GENERAL_ADMISSION',2500,1,2500)
                """, item, order, UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("INSERT INTO order_item_units(id,order_item_id,unit_number) VALUES (?,?,1)",
                UUID.randomUUID(), item);
        jdbc.update("""
                INSERT INTO order_tickets(id,order_id,order_item_id,ticket_id,unit_number,status)
                VALUES (?,?,?,?,1,'ISSUED')
                """, UUID.randomUUID(), order, item, ticket);
        jdbc.update("""
                INSERT INTO refund_requests(id,ticket_id,order_id,order_item_id,customer_id,event_id,
                                            amount,status,decision)
                VALUES (?,?,?,?,?,?,2500,'REJECTED','REJECTED')
                """, rejected, ticket, order, item, customer, event);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        var mapper = new ObjectMapper();
        var refunds = new RefundDecisionController(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), mapper, base, base, base);
        mvc = MockMvcBuilders.standaloneSetup(new EventCancellationController(jdbc, refunds, mapper, base)).build();
    }

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void rejectedCustomerRequestRemainsAndCancellationRefundsExactlyOnce() throws Exception {
        mvc.perform(post("/api/events/" + event + "/cancellation")
                .header("X-User-Role", "CUSTOMER").header("X-User-Id", customer))
                .andExpect(status().isForbidden());
        assertEquals(0, revokes.get());
        mvc.perform(post("/api/events/" + event + "/cancellation")
                .header("X-User-Role", "ADMIN").header("X-User-Id", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalTickets").value(1))
                .andExpect(jsonPath("$.completedTickets").value(1));
        mvc.perform(post("/api/events/" + event + "/cancellation")
                .header("X-User-Role", "ADMIN").header("X-User-Id", customer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/events/" + event + "/cancellation").header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertEquals("REJECTED", jdbc.queryForObject("SELECT decision FROM refund_requests WHERE id=?", String.class, rejected));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM refund_requests WHERE ticket_id=?", Integer.class, ticket));
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT progress FROM refund_requests WHERE ticket_id=? AND source='CANCELLATION'", String.class, ticket));
        assertEquals(1, revokes.get());
        assertEquals(1, repayments.get());
        assertEquals(1, releases.get());
    }

    @Test void processesMoreThanOnePageAndReportsDurableProgress() throws Exception {
        jdbc.update("UPDATE purchase_order_items SET quantity=21,subtotal=52500 WHERE id=?", item);
        jdbc.update("UPDATE purchase_orders SET total_amount=52500 WHERE id=?", order);
        for (int unit = 2; unit <= 21; unit++) {
            jdbc.update("INSERT INTO order_item_units(id,order_item_id,unit_number) VALUES (?,?,?)",
                    UUID.randomUUID(), item, unit);
            jdbc.update("""
                    INSERT INTO order_tickets(id,order_id,order_item_id,ticket_id,unit_number,status)
                    VALUES (?,?,?,?,?,'ISSUED')
                    """, UUID.randomUUID(), order, item, UUID.randomUUID(), unit);
        }
        String path = "/api/events/" + event + "/cancellation";
        mvc.perform(post(path).header("X-User-Role", "ADMIN").header("X-User-Id", customer))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.totalTickets").value(21))
                .andExpect(jsonPath("$.completedTickets").value(20));
        mvc.perform(get(path).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pendingTickets").value(1));
        mvc.perform(post(path).header("X-User-Role", "ADMIN").header("X-User-Id", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completedTickets").value(21));
        assertEquals(21, jdbc.queryForObject("""
                SELECT COUNT(DISTINCT ticket_id) FROM refund_requests
                WHERE event_id=? AND progress='COMPLETED'
                """, Integer.class, event));
        assertEquals(21, repayments.get());
        mvc.perform(post(path).header("X-User-Role", "ADMIN").header("X-User-Id", customer))
                .andExpect(status().isOk());
        assertEquals(21, repayments.get());
    }

    private void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body;
        if (path.endsWith("/cancel")) body = "{\"status\":\"CANCELLED\"}";
        else if (path.endsWith("/revoke")) { revokes.incrementAndGet(); body = "{\"status\":\"REVOKED\"}"; }
        else if (path.endsWith("/refunds")) { repayments.incrementAndGet(); body = "{\"status\":\"REFUNDED\"}"; }
        else if (path.endsWith("/sold-units/release")) {
            releases.incrementAndGet(); body = "{\"reservationId\":\"" + reservation + "\"}";
        } else { exchange.sendResponseHeaders(404, -1); exchange.close(); return; }
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, data.length);
        try (var output = exchange.getResponseBody()) { output.write(data); }
    }
}
