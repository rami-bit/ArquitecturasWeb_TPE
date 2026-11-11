package com.ticketya.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD)
class RefundDecisionIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4");

    private final UUID customer = UUID.randomUUID();
    private final UUID order = UUID.randomUUID();
    private final UUID item = UUID.randomUUID();
    private final UUID event = UUID.randomUUID();
    private final UUID sector = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private final UUID payment = UUID.randomUUID();
    private final UUID reservation = UUID.randomUUID();
    private final UUID refund = UUID.randomUUID();
    private final AtomicInteger revokes = new AtomicInteger();
    private final AtomicInteger refunds = new AtomicInteger();
    private final AtomicInteger releases = new AtomicInteger();
    private final AtomicReference<String> failOnce = new AtomicReference<>();
    private final AtomicReference<String> paymentBody = new AtomicReference<>();
    private HttpServer server;
    private ExecutorService serverThreads;
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach void setup() throws IOException {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM refund_requests");
        jdbc.update("DELETE FROM purchase_order_items");
        jdbc.update("DELETE FROM purchase_orders");
        jdbc.update("""
                INSERT INTO purchase_orders(id,customer_id,event_id,event_name,event_starts_at,venue_name,venue_city,
                    payment_method,payment_outcome,status,idempotency_key,request_fingerprint,payment_id,reservation_id,total)
                VALUES (?,?,?,'Show','2027-01-01T20:00:00Z','Hall','Buenos Aires','CARD','APPROVED','PAID',?,
                    'fingerprint',?,?,2500)
                """, order,customer,event,UUID.randomUUID().toString(),payment,reservation);
        jdbc.update("""
                INSERT INTO purchase_order_items(id,order_id,sector_id,sector_name,unit_price,quantity,ticket_payload)
                VALUES (?,?,?,'General',2500,1,?::text)
                """, item,order,sector,"{\"tickets\":[{\"ticketId\":\""+ticket+"\",\"unitNumber\":1}]}");
        jdbc.update("""
                INSERT INTO refund_requests(id,ticket_id,order_id,order_item_id,customer_id,event_id,amount)
                VALUES (?,?,?,?,?,?,2500)
                """, refund,ticket,order,item,customer,event);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",this::respond);
        serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.start();
        String base = "http://127.0.0.1:"+server.getAddress().getPort();
        mvc = MockMvcBuilders.standaloneSetup(new RefundDecisionController(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),new ObjectMapper(),
                base,base,base)).build();
    }

    @AfterEach void stop() {
        if (server != null) server.stop(0);
        if (serverThreads != null) serverThreads.shutdownNow();
    }

    @Test void onlyAdminDecidesAndRejectedRequestHasNoDownstreamEffects() throws Exception {
        mvc.perform(decide("REJECTED").header("X-User-Role","CUSTOMER"))
                .andExpect(status().isForbidden());
        mvc.perform(decide("REJECTED").header("X-User-Role","ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(decide("REJECTED").header("X-User-Role","ADMIN"))
                .andExpect(status().isOk());
        mvc.perform(decide("APPROVED").header("X-User-Role","ADMIN"))
                .andExpect(status().isConflict());
        assertEquals("REJECTED",jdbc.queryForObject("SELECT decision FROM refund_requests WHERE id=?",String.class,refund));
        assertEquals(0,revokes.get());
        assertEquals(0,refunds.get());
        assertEquals(0,releases.get());
    }

    @Test void approvedRefundRetainsProgressAndRetriesEachFailedDependency() throws Exception {
        for (String failingStep : new String[] {"revoke","refund","release"}) {
            resetRequest();
            failOnce.set(failingStep);
            mvc.perform(decide("APPROVED").header("X-User-Role","ADMIN"))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.decision").value("APPROVED"));
            if (!"release".equals(failingStep))
                assertEquals(0,releases.get(),"Inventory must stay sold until payment confirms");
            else assertEquals("PAYMENT_REFUNDED",jdbc.queryForObject(
                    "SELECT progress FROM refund_requests WHERE id=?",String.class,refund));
            mvc.perform(decide("APPROVED").header("X-User-Role","ADMIN"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.progress").value("COMPLETED"));
            assertEquals("revoke".equals(failingStep)?2:1,revokes.get());
            assertEquals("refund".equals(failingStep)?2:1,refunds.get());
            assertEquals("release".equals(failingStep)?2:1,releases.get());
            int afterRevoke=revokes.get(), afterRefund=refunds.get(), afterRelease=releases.get();
            mvc.perform(decide("APPROVED").header("X-User-Role","ADMIN"))
                    .andExpect(status().isOk());
            assertEquals(afterRevoke,revokes.get(),"Completed request must not repeat revocation");
            assertEquals(afterRefund,refunds.get(),"Completed request must not repeat payment");
            assertEquals(afterRelease,releases.get(),"Completed request must not repeat inventory release");
            assertEquals(2500,new ObjectMapper().readTree(paymentBody.get()).path("amount").decimalValue().intValue());
            assertTrue(paymentBody.get().contains(refund.toString()));
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder decide(String decision) {
        return post("/api/refunds/"+refund+"/decision")
                .contentType("application/json").content("{\"decision\":\""+decision+"\"}");
    }

    private void resetRequest() {
        jdbc.update("UPDATE refund_requests SET decision=NULL,status='PENDING',progress='NOT_STARTED' WHERE id=?",refund);
        revokes.set(0);refunds.set(0);releases.set(0);paymentBody.set(null);
    }

    private void respond(HttpExchange exchange) throws IOException {
        String path=exchange.getRequestURI().getPath();
        String step;
        String body="{}";
        if (path.endsWith("/revoke")) {
            step="revoke";revokes.incrementAndGet();
            body="{\"ticketId\":\""+ticket+"\",\"status\":\"REVOKED\"}";
        }
        else if (path.endsWith("/refunds")) {
            step="refund";refunds.incrementAndGet();
            paymentBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            body="{\"refundId\":\""+refund+"\",\"status\":\"REFUNDED\"}";
        } else if (path.endsWith("/sold-units/release")) {
            step="release";releases.incrementAndGet();
            body="{\"reservationId\":\""+reservation+"\",\"alreadyReleased\":false}";
        }
        else {step="unknown";}
        int status="unknown".equals(step)?404:failOnce.compareAndSet(step,null)?503:200;
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json");
        exchange.sendResponseHeaders(status,bytes.length);
        try(var out=exchange.getResponseBody()){out.write(bytes);}
    }
}
