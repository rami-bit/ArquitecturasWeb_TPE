package com.ticketya.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD)
class PurchaseFlowIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");
    private static final UUID EVENT = UUID.randomUUID();
    private static final UUID SECTOR = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID PAYMENT = UUID.randomUUID();
    private static final UUID RESERVATION = UUID.randomUUID();

    private HttpServer server;
    private ExecutorService serverThreads;
    private JdbcTemplate jdbc;
    private PurchaseController controller;
    private final AtomicBoolean expireConfirmation = new AtomicBoolean();
    private final AtomicBoolean failTicketOnce = new AtomicBoolean();
    private final AtomicBoolean failReserveOnce = new AtomicBoolean();
    private final AtomicInteger charges = new AtomicInteger();
    private final AtomicInteger refunds = new AtomicInteger();
    private final AtomicInteger releases = new AtomicInteger();
    private final AtomicInteger ticketCalls = new AtomicInteger();

    @BeforeEach void setup() throws IOException {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM refund_requests");
        jdbc.update("DELETE FROM purchase_order_items");
        jdbc.update("DELETE FROM purchase_orders");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        controller = new PurchaseController(jdbc, new ObjectMapper(),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), base, base, base, base, 4, 60);
    }

    @AfterEach void stop() {
        if (server != null) server.stop(0);
        if (serverThreads != null) serverThreads.shutdownNow();
    }

    @Test void refundRequestUsesCurrentEventDateAndHidesOtherOwners() throws Exception {
        UUID order = UUID.randomUUID(), item = UUID.randomUUID(), ticket = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO purchase_orders(id,customer_id,event_id,event_name,event_starts_at,venue_name,venue_city,
                payment_method,payment_outcome,status,idempotency_key,request_fingerprint,total)
                VALUES (?,?,?,'Show','2027-01-01T20:00:00Z','Hall','Buenos Aires','CARD','APPROVED','PAID',?,'fingerprint',2500)
                """,order,CUSTOMER,EVENT,UUID.randomUUID().toString());
        jdbc.update("""
                INSERT INTO purchase_order_items(id,order_id,sector_id,sector_name,unit_price,quantity,ticket_payload)
                VALUES (?,?,?,'General',2500,1,?::text)
                """,item,order,SECTOR,"{\"tickets\":[{\"ticketId\":\""+ticket+"\"}]}");
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        Clock valid = Clock.fixed(Instant.parse("2026-12-20T00:00:00Z"),ZoneOffset.UTC);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RefundRequestController(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(new DriverManagerDataSource(
                        postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()))),
                new ObjectMapper(),valid,base)).build();
        String path = "/api/refunds/tickets/"+ticket;
        mvc.perform(post(path).header("X-User-Id",UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(post(path).header("X-User-Id",CUSTOMER)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post(path).header("X-User-Id",CUSTOMER)).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM refund_requests",Integer.class));
        UUID refundId=jdbc.queryForObject("SELECT id FROM refund_requests WHERE ticket_id=?",UUID.class,ticket);
        mvc.perform(get("/api/refunds/"+refundId).header("X-User-Id",UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/refunds/"+refundId).header("X-User-Id",CUSTOMER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ticketId").value(ticket.toString()));
        Clock boundary=Clock.fixed(Instant.parse("2026-12-29T20:00:00Z"),ZoneOffset.UTC);
        MockMvc late=MockMvcBuilders.standaloneSetup(new RefundRequestController(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(new DriverManagerDataSource(
                        postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()))),
                new ObjectMapper(),boundary,base)).build();
        jdbc.update("DELETE FROM refund_requests");
        late.perform(post(path).header("X-User-Id",CUSTOMER)).andExpect(status().isConflict());
    }

    @Test void approvedAndRejectedPurchasesPersistSnapshotsAndDoNotDuplicateCharges() {
        String key = "approved-" + UUID.randomUUID();
        var approved = controller.create(CUSTOMER, key, "trace-1", request(1, "APPROVED"));
        assertEquals("PAID", approved.getBody().status());
        var repeated = controller.create(CUSTOMER, key, "trace-1", request(1, "APPROVED"));
        assertEquals(approved.getBody().orderId(), repeated.getBody().orderId());
        assertEquals(1, charges.get());
        assertEquals(1, ticketCalls.get());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM purchase_orders WHERE status='RECOVERY_REQUIRED'", Integer.class));
        assertEquals(2500, jdbc.queryForObject("SELECT unit_price FROM purchase_order_items", java.math.BigDecimal.class).intValue());
        assertEquals(approved.getBody().orderId(), controller.get(CUSTOMER, approved.getBody().orderId()).orderId());
        assertThrows(ResponseStatusException.class, () -> controller.get(UUID.randomUUID(), approved.getBody().orderId()));
        var rejected = controller.create(CUSTOMER, "rejected-" + UUID.randomUUID(), "trace-2", request(1, "REJECTED"));
        assertEquals("REJECTED", rejected.getBody().status());
        assertEquals(1, releases.get());
        assertEquals(1, ticketCalls.get());
    }

    @Test void concurrentOrdersRespectCustomerEventLimit() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var one = pool.submit(() -> { gate.await(); return controller.create(CUSTOMER, UUID.randomUUID().toString(), "concurrent", request(3, "APPROVED")); });
            var two = pool.submit(() -> { gate.await(); return controller.create(CUSTOMER, UUID.randomUUID().toString(), "concurrent", request(3, "APPROVED")); });
            gate.countDown();
            int successes = 0, conflicts = 0;
            for (var result : List.of(one, two)) {
                try { assertEquals("PAID", result.get().getBody().status()); successes++; }
                catch (java.util.concurrent.ExecutionException ex) {
                    assertInstanceOf(ResponseStatusException.class, ex.getCause()); conflicts++;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, conflicts);
            assertEquals(3, jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM purchase_order_items", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void expiredHoldRequestsRefundAndReleasesHoldWithoutIssuingTickets() {
        expireConfirmation.set(true);
        var result = controller.create(CUSTOMER, UUID.randomUUID().toString(), "expiry", request(1, "APPROVED"));
        assertEquals("RECOVERY_REQUIRED", result.getBody().status());
        assertEquals(1, refunds.get());
        assertEquals(1, releases.get());
        assertEquals(0, ticketCalls.get());
    }

    @Test void ticketOutageCanBeRetriedWithSameOrder() {
        failTicketOnce.set(true);
        String key = UUID.randomUUID().toString();
        var first = controller.create(CUSTOMER, key, "retry", request(1, "APPROVED"));
        assertEquals("RECOVERY_REQUIRED", first.getBody().status());
        var second = controller.create(CUSTOMER, key, "retry", request(1, "APPROVED"));
        assertEquals(first.getBody().orderId(), second.getBody().orderId());
        assertEquals("PAID", second.getBody().status());
        assertEquals(2, ticketCalls.get());
    }

    @Test void temporaryInventoryOutageRetainsOrderAndRetriesWithoutChargingEarly() {
        failReserveOnce.set(true);
        String key = UUID.randomUUID().toString();
        var first = controller.create(CUSTOMER, key, "inventory-retry", request(1, "APPROVED"));
        assertEquals("RECOVERY_REQUIRED", first.getBody().status());
        assertEquals(0, charges.get());
        var second = controller.create(CUSTOMER, key, "inventory-retry", request(1, "APPROVED"));
        assertEquals(first.getBody().orderId(), second.getBody().orderId());
        assertEquals("PAID", second.getBody().status());
        assertEquals(1, charges.get());
    }

    private PurchaseController.PurchaseRequest request(int quantity, String outcome) {
        return new PurchaseController.PurchaseRequest(EVENT,
                List.of(new PurchaseController.ItemRequest(SECTOR, null, quantity, null, null)), "CARD", outcome);
    }

    private void respond(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        int status = 200;
        String body;
        if (path.equals("/api/events/" + EVENT)) {
            body = "{\"id\":\"" + EVENT + "\",\"name\":\"Show\",\"startsAt\":\"2027-01-01T20:00:00Z\",\"venueName\":\"Hall\",\"city\":\"Buenos Aires\",\"sectors\":[{\"id\":\"" + SECTOR + "\",\"name\":\"General\",\"price\":2500}]}";
        } else if (path.equals("/internal/reservations")) {
            status = failReserveOnce.compareAndSet(true, false) ? 503 : 200;
            body = "{\"reservationId\":\"" + RESERVATION + "\"}";
        } else if (path.endsWith("/confirm")) {
            status = expireConfirmation.get() ? 409 : 200; body = "{}";
        } else if (path.endsWith("/release")) {
            releases.incrementAndGet(); body = "{}";
        } else if (path.endsWith("/refunds")) {
            refunds.incrementAndGet(); body = "{\"refundId\":\"" + UUID.randomUUID() + "\"}";
        } else if (path.equals("/internal/payments")) {
            charges.incrementAndGet();
            String input = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            body = "{\"paymentId\":\"" + PAYMENT + "\",\"status\":\"" + (input.contains("REJECTED") ? "REJECTED" : "APPROVED") + "\"}";
        } else if (path.endsWith("/tickets")) {
            ticketCalls.incrementAndGet(); status = failTicketOnce.compareAndSet(true, false) ? 503 : 201;
            body = status == 201 ? "{\"tickets\":[{\"ticketId\":\"" + UUID.randomUUID() + "\"}]}" : "{}";
        } else { status = 404; body = "{}"; }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }
}
