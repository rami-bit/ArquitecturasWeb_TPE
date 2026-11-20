package com.ticketya.catalog.api.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ticketya.catalog.api.error.GlobalExceptionHandler;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.ticketya.catalog.application.recommendation.RecommendationCandidateService;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato interno de candidatos de recomendación contra PostgreSQL real. */
@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD)
class RecommendationCandidatesIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.4-alpine");

    private final UUID venueId = UUID.randomUUID();
    private final UUID early = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final UUID late = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private final UUID draft = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private final UUID past = UUID.fromString("00000000-0000-4000-8000-000000000004");

    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        for (String table : List.of("snapshot_seats", "snapshot_sectors", "snapshot_participants",
                "event_snapshots", "events", "venues")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO venues(id,name,address,city,latitude,longitude) VALUES (?,?,?,?,?,?)",
                venueId, "Sala", "Calle 1", "Buenos Aires",
                new BigDecimal("-34.600000"), new BigDecimal("-58.380000"));

        seed(late, "PUBLISHED", "2030-07-01T21:00:00Z", "Tarde", "MUSIC", "Artista B", "7000.00");
        seed(early, "PUBLISHED", "2030-06-01T21:00:00Z", "Temprano", "CONCIERTO", "Artista A", "5000.00");
        seed(draft, "DRAFT", "2030-05-01T21:00:00Z", "Borrador", "TEATRO", "Artista C", "3000.00");
        seed(past, "PUBLISHED", "2020-01-01T21:00:00Z", "Pasado", "MUSIC", "Artista D", "2000.00");

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mvc = MockMvcBuilders.standaloneSetup(
                        new RecommendationCandidateController(new RecommendationCandidateService(jdbc)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void soloPublicadosFuturosOrdenadosPorFechaYConParticipantes() throws Exception {
        mvc.perform(get("/internal/events/recommendation-candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].eventId").value(early.toString()))
                .andExpect(jsonPath("$.items[0].name").value("Temprano"))
                .andExpect(jsonPath("$.items[0].eventType").value("CONCIERTO"))
                .andExpect(jsonPath("$.items[0].city").value("Buenos Aires"))
                .andExpect(jsonPath("$.items[0].participants[0]").value("Artista A"))
                .andExpect(jsonPath("$.items[0].minPrice").value(5000.0))
                .andExpect(jsonPath("$.items[1].eventId").value(late.toString()))
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void limiteEsAplicableYValidaRango() throws Exception {
        mvc.perform(get("/internal/events/recommendation-candidates").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].eventId").value(early.toString()));

        mvc.perform(get("/internal/events/recommendation-candidates").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mvc.perform(get("/internal/events/recommendation-candidates").param("limit", "201"))
                .andExpect(status().isBadRequest());
    }

    private void seed(UUID eventId, String status, String startsAt, String name,
                      String type, String participant, String price) {
        UUID snapshotId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO events(id,name,description,event_type,starts_at,organizer_id,venue_id,status)
                        VALUES (?,?,?,?,?,?,?,?)
                        """,
                eventId, name, "Descripcion de " + name, type,
                OffsetDateTime.parse(startsAt), UUID.randomUUID(), venueId, status);
        jdbc.update("""
                        INSERT INTO event_snapshots(
                            id,event_id,name,description,event_type,starts_at,
                            venue_name,venue_address,venue_city,venue_latitude,venue_longitude)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?)
                        """,
                snapshotId, eventId, name, "Descripcion de " + name, type,
                OffsetDateTime.parse(startsAt), "Sala", "Calle 1", "Buenos Aires",
                new BigDecimal("-34.600000"), new BigDecimal("-58.380000"));
        jdbc.update("INSERT INTO snapshot_participants(id,snapshot_id,name) VALUES (?,?,?)",
                UUID.randomUUID(), snapshotId, participant);
        jdbc.update("""
                        INSERT INTO snapshot_sectors(
                            id,snapshot_id,source_sector_id,name,sector_type,capacity,price)
                        VALUES (?,?,?,?,?,?,?)
                        """,
                UUID.randomUUID(), snapshotId, UUID.randomUUID(), "Campo",
                "GENERAL_ADMISSION", 100, new BigDecimal(price));
    }
}
