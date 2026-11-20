package com.ticketya.preferences.integration;

import com.ticketya.preferences.api.error.ApiProblemException;
import com.ticketya.preferences.api.preferences.CustomerPreferencesRequest;
import com.ticketya.preferences.api.preferences.CustomerPreferencesResponse;
import com.ticketya.preferences.application.preferences.PreferencesService;
import com.ticketya.preferences.domain.preferences.CustomerPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Persistencia real en MongoDB 7 con Testcontainers: sin mocks ni H2. */
@SpringBootTest
@Testcontainers
class CustomerPreferencesMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("preferences.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("preferences.mongodb.database", () -> "ticketya_preferences_test");
    }

    @Autowired
    private PreferencesService service;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanCollection() {
        mongoTemplate.dropCollection(CustomerPreferences.class);
    }

    @Test
    void altaYReemplazoGuardanUnSoloDocumentoConIdIgualAlCliente() {
        UUID customerId = UUID.randomUUID();
        CustomerPreferencesResponse first = service.replace(customerId,
                new CustomerPreferencesRequest(" Buenos Aires ", List.of("CONCIERTO"), List.of("Artista A")));
        CustomerPreferencesResponse second = service.replace(customerId,
                new CustomerPreferencesRequest("Rosario", List.of("TEATRO"), List.of("Artista B")));

        assertThat(first.version()).isEqualTo(1L);
        assertThat(first.city()).isEqualTo("Buenos Aires");
        assertThat(first.updatedAt()).isNotNull();
        assertThat(second.version()).isEqualTo(2L);
        assertThat(countDocuments(customerId)).isEqualTo(1L);
        assertThat(service.get(customerId).city()).isEqualTo("Rosario");
        assertThat(service.get(customerId).version()).isEqualTo(2L);
    }

    @Test
    void getSinDocumentoDevuelveVersionCeroYNoCreaDocumento() {
        UUID customerId = UUID.randomUUID();

        CustomerPreferencesResponse response = service.get(customerId);

        assertThat(response.city()).isNull();
        assertThat(response.eventTypes()).isEmpty();
        assertThat(response.favoriteArtists()).isEmpty();
        assertThat(response.updatedAt()).isNull();
        assertThat(response.version()).isZero();
        assertThat(countDocuments(customerId)).isZero();
    }

    @Test
    void escriturasConcurrentesConservanUnUnicoDocumento() throws Exception {
        UUID customerId = UUID.randomUUID();
        CompletableFuture<CustomerPreferencesResponse> first = CompletableFuture.supplyAsync(() ->
                service.replace(customerId,
                        new CustomerPreferencesRequest("Buenos Aires", List.of("CONCIERTO"), List.of())));
        CompletableFuture<CustomerPreferencesResponse> second = CompletableFuture.supplyAsync(() ->
                service.replace(customerId,
                        new CustomerPreferencesRequest("Rosario", List.of("TEATRO"), List.of())));

        CompletableFuture.allOf(first, second).join();

        assertThat(countDocuments(customerId)).isEqualTo(1L);
        assertThat(service.get(customerId).version()).isEqualTo(2L);
    }

    @Test
    void validacionesRechazanCiudadVaciaYDeminados() {
        UUID customerId = UUID.randomUUID();
        assertThatThrownBy(() -> service.replace(customerId,
                new CustomerPreferencesRequest("   ", List.of(), List.of())))
                .isInstanceOf(ApiProblemException.class)
                .hasMessageContaining("city");

        List<String> artists = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            artists.add("Artista " + index);
        }
        assertThatThrownBy(() -> service.replace(customerId,
                new CustomerPreferencesRequest(null, List.of(), artists)))
                .isInstanceOf(ApiProblemException.class)
                .hasMessageContaining("favoriteArtists");

        assertThatThrownBy(() -> service.replace(customerId,
                new CustomerPreferencesRequest(null, List.of("Concierto", "CONCIERTO"), List.of())))
                .isInstanceOf(ApiProblemException.class)
                .hasMessageContaining("duplicados");

        assertThat(countDocuments(customerId)).isZero();
    }

    private long countDocuments(UUID customerId) {
        return mongoTemplate.getCollection("customer_preferences")
                .countDocuments(org.bson.Document.parse("{\"_id\":\"" + customerId + "\"}"));
    }
}
