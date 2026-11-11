package com.ticketya.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticketya.purchase.api.refund.RefundRequestController;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-F7.1 de punta a punta: sólo el titular de un ticket confirmado puede pedir
 * la devolución, la solicitud es idempotente y la ventana de 72 horas se mide
 * contra la fecha vigente del evento en Catalog.
 */
@Testcontainers(disabledWithoutDocker = true)
class RefundRequestIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");
    private static final UUID EVENT = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private final UUID order = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID sector = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private HttpServer server;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;

    @BeforeEach void setup() throws IOException {
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM refund_requests");
        jdbc.update("DELETE FROM order_tickets");
        jdbc.update("DELETE FROM order_item_units");
        jdbc.update("DELETE FROM purchase_order_items");
        jdbc.update("DELETE FROM purchase_orders");
        jdbc.update("""
                INSERT INTO purchase_orders(id,customer_id,event_id,event_name,event_starts_at,venue_name,
                    venue_city,payment_method,status,idempotency_key,request_fingerprint,total_amount)
                VALUES (?,?,?,'Show','2027-01-01T20:00:00Z','Hall','Buenos Aires','CARD','CONFIRMED',?,
                    'fingerprint',2500)
                """, order, CUSTOMER, EVENT, UUID.randomUUID().toString());
        jdbc.update("""
                INSERT INTO purchase_order_items(id,order_id,inventory_sector_id,source_sector_id,sector_name,
                    sector_type,quantity,unit_price,subtotal)
                VALUES (?,?,?,?,'General','GENERAL_ADMISSION',1,2500,2500)
                """, item, order, sector, sector);
        jdbc.update("INSERT INTO order_item_units(id,order_item_id,unit_number) VALUES (?,?,1)",
                UUID.randomUUID(), item);
        jdbc.update("""
                INSERT INTO order_tickets(id,order_id,order_item_id,ticket_id,unit_number,status)
                VALUES (?,?,?,?,1,'ISSUED')
                """, UUID.randomUUID(), order, item, ticket);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
    }

    @AfterEach void stop() {
        if (server != null) server.stop(0);
    }

    @Test void refundRequestUsesCurrentEventDateAndHidesOtherOwners() throws Exception {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        Clock valid = Clock.fixed(Instant.parse("2026-12-20T00:00:00Z"), ZoneOffset.UTC);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RefundRequestController(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new ObjectMapper(), valid, base)).build();
        String path = "/api/refunds/tickets/" + ticket;
        mvc.perform(post(path).header("X-User-Id", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(post(path).header("X-User-Id", CUSTOMER)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post(path).header("X-User-Id", CUSTOMER)).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM refund_requests", Integer.class));
        UUID refundId = jdbc.queryForObject(
                "SELECT id FROM refund_requests WHERE ticket_id=?", UUID.class, ticket);
        mvc.perform(get("/api/refunds/" + refundId).header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/refunds/" + refundId).header("X-User-Id", CUSTOMER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ticketId").value(ticket.toString()));
        Clock boundary = Clock.fixed(Instant.parse("2026-12-29T20:00:00Z"), ZoneOffset.UTC);
        MockMvc late = MockMvcBuilders.standaloneSetup(new RefundRequestController(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new ObjectMapper(), boundary, base)).build();
        jdbc.update("DELETE FROM refund_requests");
        late.perform(post(path).header("X-User-Id", CUSTOMER)).andExpect(status().isConflict());
    }

    private void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body;
        if (path.equals("/api/events/" + EVENT)) {
            body = "{\"id\":\"" + EVENT + "\",\"startsAt\":\"2027-01-01T20:00:00Z\"}";
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
