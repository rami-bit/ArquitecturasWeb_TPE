package com.ticketya.catalog.api.venue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.infrastructure.persistence.venue.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:venue-management;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
@Transactional
class VenueManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired VenueRepository repository;

    @Test
    void adminCanCreateAndModifyVenueAndItsTemplate() throws Exception {
        String body = """
                {"name":"Arena","address":"Av 1","city":"BA","latitude":-34.6,"longitude":-58.4,
                 "sectors":[{"name":"Plateas","type":"NUMBERED","seats":[{"rowLabel":"A","seatNumber":1}]},
                 {"name":"Campo","type":"GENERAL_ADMISSION","capacity":50}]}
                """;
        String response = mvc.perform(post("/api/venues").header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sectors.length()").value(2)).andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("id").asText();

        String update_body ="""
            {"name":"Arena Norte","address":"Av 2","city":"BA","latitude":-34.5,"longitude":-58.3,
            "sectors":[{"name":"Campo","type":"GENERAL_ADMISSION","capacity":80}]}
            """;
        mvc.perform(put("/api/venues/" + id).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update_body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Arena Norte"))
                .andExpect(jsonPath("$.sectors[0].capacity").value(80));
        assertThat(repository.findById(java.util.UUID.fromString(id)).orElseThrow().getName()).isEqualTo("Arena Norte");
    }

    @Test
    void nonAdminCannotCreateOrChangeExistingVenue() throws Exception {
        Venue venue = repository.saveAndFlush(new Venue("Arena", "Av 1", "BA", BigDecimal.ZERO, BigDecimal.ZERO));
        String body = """
                {"name":"Changed","address":"Av 2","city":"BA","latitude":0,"longitude":0}
                """;
        for (String role : new String[]{"ORGANIZER", "CLIENT"}) {
            mvc.perform(post("/api/venues").header("X-User-Role", role).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/venues/" + venue.getId()).header("X-User-Role", role)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(venue.getId()).orElseThrow().getName()).isEqualTo("Arena");
    }

    @Test
    void invalidSectorReplacementDoesNotPartiallyChangeVenue() throws Exception {
        Venue venue = new Venue("Arena", "Av 1", "BA", BigDecimal.ZERO, BigDecimal.ZERO);
        venue.addGeneralAdmissionSector("Original", 10);
        venue = repository.saveAndFlush(venue);
        String body = """
                {"name":"Changed","address":"Av 2","city":"BA","latitude":1,"longitude":1,"sectors":[{"name":"Invalid","type":"GENERAL_ADMISSION","capacity":0}]}
                """;
        mvc.perform(put("/api/venues/" + venue.getId()).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        Venue unchanged = repository.findById(venue.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Arena");
        assertThat(unchanged.getSectors()).extracting("name").containsExactly("Original");
    }

    @Test
    void listsVenuesWithPaginationForOrganizerAndAdminButRejectsClient() throws Exception {
        repository.save(new Venue("Arena A", "Av 1", "BA", BigDecimal.ZERO, BigDecimal.ZERO));
        repository.save(new Venue("Arena B", "Av 2", "BA", BigDecimal.ZERO, BigDecimal.ZERO));
        for (String role : new String[]{"ORGANIZER", "ADMIN"}) {
            mvc.perform(get("/api/venues?page=0&size=1").header("X-User-Role", role))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(1))
                    .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        }
        mvc.perform(get("/api/venues").header("X-User-Role", "CLIENT"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/venues?page=-1&size=0").header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generatedOpenApiDocumentsVenueEndpointsAndSectorExamples() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/venues'].get").exists())
                .andExpect(jsonPath("$.paths['/api/venues'].post").exists())
                .andExpect(jsonPath("$.paths['/api/venues/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/venues'].get.responses['200'].content['application/json'].examples").exists());
    }
}

