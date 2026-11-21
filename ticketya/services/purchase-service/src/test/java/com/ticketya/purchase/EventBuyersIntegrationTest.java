package com.ticketya.purchase;

import com.ticketya.purchase.api.notification.EventBuyersController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PDF-F11: compradores de un evento para avisos de cambio o cancelación, contra PostgreSQL real. */
@Testcontainers(disabledWithoutDocker = true)
class EventBuyersIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4-alpine");

    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        mvc = MockMvcBuilders.standaloneSetup(new EventBuyersController(jdbc)).build();
    }

    @Test
    void returnsDistinctCustomersWithPaidOrdersOnly() throws Exception {
        UUID event = UUID.randomUUID();
        UUID paidTwice = UUID.randomUUID();
        UUID paidPendingTickets = UUID.randomUUID();
        order(event, paidTwice, "CONFIRMED");
        order(event, paidTwice, "CONFIRMED");
        order(event, paidPendingTickets, "PAID_PENDING_TICKETS");
        for (String unpaid : List.of("REJECTED", "COMPENSATED", "PAYMENT_PENDING")) {
            order(event, UUID.randomUUID(), unpaid);
        }
        order(UUID.randomUUID(), UUID.randomUUID(), "CONFIRMED");

        mvc.perform(get("/internal/events/" + event + "/buyers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.toString()))
                .andExpect(jsonPath("$.customerIds", hasSize(2)))
                .andExpect(jsonPath("$.customerIds", containsInAnyOrder(paidTwice.toString(),
                        paidPendingTickets.toString())));
    }

    @Test
    void eventWithoutSalesReturnsAnEmptyList() throws Exception {
        mvc.perform(get("/internal/events/" + UUID.randomUUID() + "/buyers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerIds", hasSize(0)));
    }

    private void order(UUID event, UUID customer, String status) {
        jdbc.update("""
                INSERT INTO purchase_orders(id,customer_id,event_id,event_name,event_starts_at,venue_name,venue_city,
                    payment_method,payment_status,status,idempotency_key,request_fingerprint,total_amount)
                VALUES (?,?,?,'Show','2027-01-01T20:00:00Z','Hall','Rosario','CARD','APPROVED',?,?,'f',1)
                """, UUID.randomUUID(), customer, event, status, UUID.randomUUID().toString());
    }
}
