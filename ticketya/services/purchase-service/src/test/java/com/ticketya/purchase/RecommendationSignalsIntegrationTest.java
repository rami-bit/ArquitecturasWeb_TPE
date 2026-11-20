package com.ticketya.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.ticketya.purchase.api.error.GlobalExceptionHandler;
import com.ticketya.purchase.api.recommendation.RecommendationSignalController;
import com.ticketya.purchase.application.recommendation.RecommendationSignalsService;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato interno de señales de recomendación contra PostgreSQL real. */
@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD)
class RecommendationSignalsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4-alpine");

    private final UUID customer = UUID.randomUUID();
    private final UUID eventA = UUID.randomUUID();
    private final UUID eventB = UUID.randomUUID();
    private final UUID eventC = UUID.randomUUID();
    private final UUID eventUnpaid = UUID.randomUUID();

    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM purchase_orders");
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = MockMvcBuilders.standaloneSetup(
                        new RecommendationSignalController(new RecommendationSignalsService(jdbc)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setControllerAdvice(new GlobalExceptionHandler(mapper))
                .build();
    }

    @Test
    void clienteConComprasDevuelveEventosCompradosYCocompras() throws Exception {
        order(customer, eventA, "CONFIRMED");
        order(customer, eventB, "PAID_PENDING_TICKETS");
        order(customer, eventUnpaid, "PENDING");
        UUID other1 = UUID.randomUUID();
        UUID other2 = UUID.randomUUID();
        order(other1, eventA, "CONFIRMED");
        order(other1, eventC, "CONFIRMED");
        order(other2, eventB, "CONFIRMED");
        order(other2, eventC, "CONFIRMED");

        mvc.perform(get("/internal/customers/{customerId}/recommendation-signals", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customer.toString()))
                .andExpect(jsonPath("$.purchasedEventIds", hasSize(2)))
                .andExpect(jsonPath("$.coPurchases", hasSize(1)))
                .andExpect(jsonPath("$.coPurchases[0].eventId").value(eventC.toString()))
                .andExpect(jsonPath("$.coPurchases[0].count").value(2))
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void clienteSinComprasDevuelve200ConListasVacias() throws Exception {
        UUID unknown = UUID.randomUUID();

        mvc.perform(get("/internal/customers/{customerId}/recommendation-signals", unknown))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasedEventIds", hasSize(0)))
                .andExpect(jsonPath("$.coPurchases", hasSize(0)));
    }

    @Test
    void soloCuentanOrdenesPagadas() throws Exception {
        order(customer, eventA, "PENDING");
        order(customer, eventB, "CANCELLED");

        mvc.perform(get("/internal/customers/{customerId}/recommendation-signals", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasedEventIds", hasSize(0)));
    }

    @Test
    void cocomprasEstanTopeadasA200() throws Exception {
        order(customer, eventA, "CONFIRMED");
        for (int index = 0; index < 205; index++) {
            UUID other = UUID.randomUUID();
            UUID coEvent = UUID.nameUUIDFromBytes(("co-" + index).getBytes());
            order(other, eventA, "CONFIRMED");
            order(other, coEvent, "CONFIRMED");
        }

        mvc.perform(get("/internal/customers/{customerId}/recommendation-signals", customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coPurchases", hasSize(200)));
    }

    private void order(UUID customerId, UUID eventId, String status) {
        jdbc.update("""
                        INSERT INTO purchase_orders(
                            id, customer_id, event_id, event_name, event_starts_at,
                            venue_name, venue_city, currency, total_amount, status,
                            payment_method, idempotency_key, request_fingerprint)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                UUID.randomUUID(), customerId, eventId, "Evento " + eventId,
                OffsetDateTime.parse("2030-06-10T20:00:00Z"), "Sala", "Buenos Aires",
                "ARS", java.math.BigDecimal.valueOf(100), status, "CARD",
                "key-" + UUID.randomUUID(), "fp-" + UUID.randomUUID());
    }
}
