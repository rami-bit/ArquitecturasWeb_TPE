package com.ticketya.catalog.api.venue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class VenueListPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    MockMvc mvc;

    @Test
    void listsPersistedNumberedAndGeneralSectorsOutsideAControllerTransaction() throws Exception {
        String created = mvc.perform(post("/api/venues")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Venue list test","address":"Street 1","city":"BA","latitude":-34.6,"longitude":-58.4,
                                 "sectors":[{"name":"A Platea","type":"NUMBERED","seats":[{"rowLabel":"A","seatNumber":1}]},
                                            {"name":"Z Campo","type":"GENERAL_ADMISSION","capacity":50}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String venueId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created).path("id").asText();

        mvc.perform(get("/api/venues?page=0&size=20").header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Venue list test"))
                .andExpect(jsonPath("$.content[0].sectors.length()").value(2))
                .andExpect(jsonPath("$.content[0].sectors[0].seats[0].rowLabel").value("A"))
                .andExpect(jsonPath("$.content[0].sectors[0].capacity").value(1))
                .andExpect(jsonPath("$.content[0].sectors[1].capacity").value(50));

        mvc.perform(put("/api/venues/{id}", venueId)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Venue list updated","address":"Street 2","city":"BA","latitude":-34.6,"longitude":-58.4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Venue list updated"))
                .andExpect(jsonPath("$.sectors[0].seats[0].rowLabel").value("A"));
    }
}
