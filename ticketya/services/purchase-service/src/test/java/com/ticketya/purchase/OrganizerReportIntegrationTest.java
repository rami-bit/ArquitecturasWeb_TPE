package com.ticketya.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticketya.purchase.api.error.GlobalExceptionHandler;
import com.ticketya.purchase.api.report.OrganizerReportController;
import com.ticketya.purchase.application.report.OrganizerReportService;
import com.ticketya.purchase.infrastructure.client.CatalogClient;
import com.ticketya.purchase.infrastructure.client.InventoryClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-F12 (E2-04) contra PostgreSQL real: ventas y devoluciones por sector,
 * ocupación y recaudación del evento; ownership del organizador delegado en
 * Catalog; exclusión de órdenes no pagadas; degradación sin Inventory.
 */
@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD)
class OrganizerReportIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4-alpine");

    private final UUID organizer = UUID.randomUUID();
    private final UUID otherOrganizer = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID event = UUID.randomUUID();
    private final UUID campo = UUID.randomUUID();
    private final UUID platea = UUID.randomUUID();
    private final UUID inventoryCampo = UUID.randomUUID();
    private final UUID inventoryPlatea = UUID.randomUUID();
    private final AtomicInteger catalogCalls = new AtomicInteger();
    private volatile boolean inventoryDown;
    private volatile boolean catalogDown;
    private HttpServer server;
    private ExecutorService threads;
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws IOException {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        for (String table : List.of("refund_requests", "order_tickets", "order_item_units",
                "purchase_order_items", "purchase_orders")) {
            jdbc.update("DELETE FROM " + table);
        }
        seed();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        threads = Executors.newCachedThreadPool();
        server.setExecutor(threads);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        ObjectMapper mapper = new ObjectMapper();
        OrganizerReportService service = new OrganizerReportService(new CatalogClient(base, mapper),
                new InventoryClient(base, mapper), jdbc, Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(new OrganizerReportController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mapper)).build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        if (threads != null) threads.shutdownNow();
    }

    @Test
    void ownerGetsSoldRefundedOccupancyAndRevenueBySector() throws Exception {
        mvc.perform(report(organizer, "ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.toString()))
                .andExpect(jsonPath("$.eventName").value("Recital de prueba"))
                .andExpect(jsonPath("$.eventStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.organizerId").value(organizer.toString()))
                .andExpect(jsonPath("$.capacityAvailable").value(true))
                .andExpect(jsonPath("$.sectors", hasSize(2)))
                .andExpect(jsonPath("$.sectors[0].sectorName").value("Campo"))
                .andExpect(jsonPath("$.sectors[0].sectorId").value(inventoryCampo.toString()))
                .andExpect(jsonPath("$.sectors[0].capacity").value(100))
                .andExpect(jsonPath("$.sectors[0].unitPrice").value(5000))
                .andExpect(jsonPath("$.sectors[0].soldTickets").value(4))
                .andExpect(jsonPath("$.sectors[0].refundedTickets").value(1))
                .andExpect(jsonPath("$.sectors[0].activeTickets").value(3))
                .andExpect(jsonPath("$.sectors[0].occupancyPercent").value(3.0))
                .andExpect(jsonPath("$.sectors[0].grossRevenue").value(20000.0))
                .andExpect(jsonPath("$.sectors[0].refundedAmount").value(5000.0))
                .andExpect(jsonPath("$.sectors[0].netRevenue").value(15000.0))
                .andExpect(jsonPath("$.sectors[1].sectorName").value("Platea"))
                .andExpect(jsonPath("$.sectors[1].soldTickets").value(2))
                .andExpect(jsonPath("$.sectors[1].refundedTickets").value(0))
                .andExpect(jsonPath("$.sectors[1].occupancyPercent").value(50.0))
                .andExpect(jsonPath("$.totals.capacity").value(104))
                .andExpect(jsonPath("$.totals.soldTickets").value(6))
                .andExpect(jsonPath("$.totals.refundedTickets").value(1))
                .andExpect(jsonPath("$.totals.activeTickets").value(5))
                .andExpect(jsonPath("$.totals.occupancyPercent").value(4.81))
                .andExpect(jsonPath("$.totals.grossRevenue").value(44000.0))
                .andExpect(jsonPath("$.totals.netRevenue").value(39000.0))
                .andExpect(jsonPath("$.warnings", hasSize(0)));
    }

    @Test
    void adminCanReadAnyEventReport() throws Exception {
        mvc.perform(report(admin, "ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.soldTickets").value(6));
    }

    @Test
    void foreignOrganizerAndUnknownEventAreRejectedByCatalogOwnership() throws Exception {
        mvc.perform(report(otherOrganizer, "ORGANIZER"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/reports/events/" + UUID.randomUUID())
                        .header("X-User-Id", organizer.toString()).header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientsAndAccessStaffCannotReadReportsAndCatalogIsNotCalled() throws Exception {
        mvc.perform(report(UUID.randomUUID(), "CLIENT")).andExpect(status().isForbidden());
        mvc.perform(report(UUID.randomUUID(), "ACCESS_STAFF")).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/events/" + event).header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isForbidden());
        assertThat(catalogCalls.get()).isZero();
    }

    @Test
    void reportDegradesWhenInventoryIsUnavailable() throws Exception {
        inventoryDown = true;
        mvc.perform(report(organizer, "ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacityAvailable").value(false))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.sectors[0].capacity").value(nullValue()))
                .andExpect(jsonPath("$.sectors[0].occupancyPercent").value(nullValue()))
                .andExpect(jsonPath("$.totals.occupancyPercent").value(nullValue()))
                .andExpect(jsonPath("$.totals.soldTickets").value(6))
                .andExpect(jsonPath("$.totals.netRevenue").value(39000.0));
    }

    @Test
    void catalogUnavailableResponds503() throws Exception {
        catalogDown = true;
        mvc.perform(report(organizer, "ORGANIZER")).andExpect(status().isServiceUnavailable());
    }

    // ------------------------------------------------------------------ datos y stubs

    private org.springframework.test.web.servlet.RequestBuilder report(UUID user, String role) {
        return get("/api/reports/events/" + event).header("X-User-Id", user.toString())
                .header("X-User-Role", role).header("X-Correlation-Id", "report-test");
    }

    private void seed() {
        UUID o1 = order("CONFIRMED");
        UUID campoItem = item(o1, inventoryCampo, campo, "Campo", "GENERAL_ADMISSION", 3, 5000);
        UUID refundedTicket = tickets(o1, campoItem, 3);
        UUID o2 = order("CONFIRMED");
        UUID plateaItem = item(o2, inventoryPlatea, platea, "Platea", "NUMBERED", 2, 12000);
        UUID pendingRefundTicket = tickets(o2, plateaItem, 2);
        item(order("REJECTED"), inventoryCampo, campo, "Campo", "GENERAL_ADMISSION", 2, 5000);
        item(order("PAYMENT_PENDING"), inventoryCampo, campo, "Campo", "GENERAL_ADMISSION", 1, 5000);
        item(order("COMPENSATED"), inventoryCampo, campo, "Campo", "GENERAL_ADMISSION", 1, 5000);
        item(order("PAID_PENDING_TICKETS"), inventoryCampo, campo, "Campo", "GENERAL_ADMISSION", 1, 5000);
        // Una entrada devuelta por el cliente y luego alcanzada por la cancelación: cuenta una sola vez.
        refund(refundedTicket, o1, campoItem, "CUSTOMER", "COMPLETED");
        refund(refundedTicket, o1, campoItem, "CANCELLATION", "COMPLETED");
        refund(pendingRefundTicket, o2, plateaItem, "CUSTOMER", "NOT_STARTED");
        // Ventas de otro evento no se mezclan.
        UUID foreign = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO purchase_orders(id,customer_id,event_id,event_name,event_starts_at,venue_name,venue_city,
                    payment_method,payment_status,status,idempotency_key,request_fingerprint,total_amount)
                VALUES (?,?,?,'Otro','2027-01-01T20:00:00Z','Hall','Rosario','CARD','APPROVED','CONFIRMED',?,'f',1)
                """, foreign, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString());
        item(foreign, inventoryCampo, campo, "Campo", "GENERAL_ADMISSION", 5, 5000);
    }

    private UUID order(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO purchase_orders(id,customer_id,event_id,event_name,event_starts_at,venue_name,venue_city,
                    payment_method,payment_status,status,idempotency_key,request_fingerprint,total_amount)
                VALUES (?,?,?,'Recital de prueba','2027-01-01T20:00:00Z','Hall','Rosario','CARD','APPROVED',?,?,'f',1)
                """, id, UUID.randomUUID(), event, status, UUID.randomUUID().toString());
        return id;
    }

    private UUID item(UUID order, UUID inventorySector, UUID sourceSector, String name, String type, int quantity,
                      int price) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO purchase_order_items(id,order_id,inventory_sector_id,source_sector_id,sector_name,
                    sector_type,quantity,unit_price,subtotal)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, order, inventorySector, sourceSector, name, type, quantity, price, price * quantity);
        for (int unit = 1; unit <= quantity; unit++) {
            jdbc.update("INSERT INTO order_item_units(id,order_item_id,unit_number) VALUES (?,?,?)",
                    UUID.randomUUID(), id, unit);
        }
        return id;
    }

    private UUID tickets(UUID order, UUID item, int count) {
        UUID first = null;
        for (int unit = 1; unit <= count; unit++) {
            UUID ticket = UUID.randomUUID();
            first = first == null ? ticket : first;
            jdbc.update("""
                    INSERT INTO order_tickets(id,order_id,order_item_id,ticket_id,unit_number,status)
                    VALUES (?,?,?,?,?,'ISSUED')
                    """, UUID.randomUUID(), order, item, ticket, unit);
        }
        return first;
    }

    private void refund(UUID ticket, UUID order, UUID item, String source, String progress) {
        int price = jdbc.queryForObject("SELECT unit_price::int FROM purchase_order_items WHERE id=?",
                Integer.class, item);
        jdbc.update("""
                INSERT INTO refund_requests(id,ticket_id,order_id,order_item_id,customer_id,event_id,amount,
                    status,decision,progress,source)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), ticket, order, item, UUID.randomUUID(), event, price,
                "COMPLETED".equals(progress) ? "COMPLETED" : "PENDING",
                "COMPLETED".equals(progress) ? "APPROVED" : null, progress, source);
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            int status;
            String body;
            if (path.startsWith("/api/management/events/")) {
                catalogCalls.incrementAndGet();
                String user = exchange.getRequestHeaders().getFirst("X-User-Id");
                String role = exchange.getRequestHeaders().getFirst("X-User-Role");
                if (catalogDown) {
                    status = 503;
                    body = "{}";
                } else if (!path.endsWith(event.toString())) {
                    status = 404;
                    body = "{}";
                } else if ("ADMIN".equals(role) || organizer.toString().equals(user)) {
                    status = 200;
                    body = """
                            {"id":"%s","name":"Recital de prueba","status":"PUBLISHED",
                             "startsAt":"2027-01-01T20:00:00Z","organizerId":"%s",
                             "offerings":[{"sectorId":"%s","price":5000},{"sectorId":"%s","price":12000}]}
                            """.formatted(event, organizer, campo, platea);
                } else {
                    status = 403;
                    body = "{}";
                }
            } else if (path.equals("/api/inventory/events/" + event + "/availability") && !inventoryDown) {
                status = 200;
                body = """
                        {"eventId":"%s","sectors":[
                          {"sectorId":"%s","sourceSectorId":"%s","name":"Platea","type":"NUMBERED","capacity":4},
                          {"sectorId":"%s","sourceSectorId":"%s","name":"Campo","type":"GENERAL_ADMISSION","capacity":100}]}
                        """.formatted(event, inventoryPlatea, platea, inventoryCampo, campo);
            } else {
                status = 503;
                body = "{}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
