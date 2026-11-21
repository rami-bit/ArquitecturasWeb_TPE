package com.ticketya.preferences.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.preferences.api.preferences.CustomerPreferencesRequest;
import com.ticketya.preferences.application.preferences.PreferencesService;
import com.ticketya.preferences.domain.preferences.CustomerPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** PDF-F11: clientes con un artista favorito en la ciudad del evento, contra MongoDB 7 real. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PreferenceMatchesMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("preferences.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("preferences.mongodb.database", () -> "ticketya_preferences_matches_test");
    }

    @Autowired
    private PreferencesService preferences;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TestRestTemplate http;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void clean() {
        mongoTemplate.dropCollection(CustomerPreferences.class);
    }

    @Test
    void matchesCityAndAnyFavoriteArtistIgnoringCaseAndSpaces() throws Exception {
        UUID fan = save("Rosario", "Banda Uno");
        UUID otherArtistFan = save("rosario ", "Solista Dos", "Otro");
        save("Córdoba", "Banda Uno");
        save("Rosario", "Banda Unos");
        save(null, "Banda Uno");

        List<String> found = matches("?city=ROSARIO&artist=banda uno&artist=Solista Dos");

        assertThat(found).containsExactlyInAnyOrder(fan.toString(), otherArtistFan.toString());
    }

    @Test
    void artistNamesAreMatchedLiterallyNotAsRegex() throws Exception {
        save("Rosario", "AC/DC");
        save("Rosario", "ACxDC");
        assertThat(matches("?city=Rosario&artist=AC/DC")).hasSize(1);
        assertThat(matches("?city=Rosario&artist=.*")).isEmpty();
    }

    @Test
    void missingCityOrArtistsReturnsNoMatches() throws Exception {
        save("Rosario", "Banda Uno");
        assertThat(matches("?artist=Banda Uno")).isEmpty();
        assertThat(matches("?city=Rosario")).isEmpty();
    }

    @Test
    void tooManyArtistsIsRejected() {
        StringBuilder query = new StringBuilder("?city=Rosario");
        for (int i = 0; i < 51; i++) {
            query.append("&artist=A").append(i);
        }
        ResponseEntity<String> response = http.getForEntity("/internal/preferences/matches" + query, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private UUID save(String city, String... artists) {
        UUID customer = UUID.randomUUID();
        preferences.replace(customer, new CustomerPreferencesRequest(city, List.of(), List.of(artists)));
        return customer;
    }

    private List<String> matches(String query) throws Exception {
        ResponseEntity<String> response = http.getForEntity("/internal/preferences/matches" + query, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(response.getBody());
        return java.util.stream.StreamSupport.stream(body.path("customerIds").spliterator(), false)
                .map(JsonNode::asText).toList();
    }
}
