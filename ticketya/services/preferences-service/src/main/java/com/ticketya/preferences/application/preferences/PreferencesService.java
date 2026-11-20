package com.ticketya.preferences.application.preferences;

import com.ticketya.preferences.api.error.ApiProblemException;
import com.ticketya.preferences.api.preferences.CustomerPreferencesRequest;
import com.ticketya.preferences.api.preferences.CustomerPreferencesResponse;
import com.ticketya.preferences.domain.preferences.CustomerPreferences;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Altas y consulta de preferencias. El upsert es de un solo documento y
 * atómico: un `findAndModify` con `$inc` de `version` y `upsert=true` sobre
 * `_id` = customerId. No hay transacciones multi-documento ni control
 * optimista: gana la última escritura.
 */
@Service
public class PreferencesService {

    static final String COLLECTION = "customer_preferences";

    private final MongoTemplate mongoTemplate;

    public PreferencesService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public CustomerPreferencesResponse replace(UUID customerId, CustomerPreferencesRequest request) {
        validate(customerId, request);
        String city = trimToNull(request.city());
        List<String> eventTypes = trimList(request.eventTypes());
        List<String> favoriteArtists = trimList(request.favoriteArtists());

        Query query = new Query(Criteria.where("_id").is(customerId.toString()));
        Update update = new Update()
                .set("city", city)
                .set("eventTypes", eventTypes)
                .set("favoriteArtists", favoriteArtists)
                .set("updatedAt", Instant.now())
                .inc("version", 1);
        CustomerPreferences saved = mongoTemplate.findAndModify(
                query, update,
                new FindAndModifyOptions().upsert(true).returnNew(true),
                CustomerPreferences.class, COLLECTION);
        return toResponse(saved);
    }

    public CustomerPreferencesResponse get(UUID customerId) {
        CustomerPreferences document = mongoTemplate.findById(
                customerId.toString(), CustomerPreferences.class, COLLECTION);
        return toResponse(document);
    }

    private static CustomerPreferencesResponse toResponse(CustomerPreferences document) {
        if (document == null) {
            return new CustomerPreferencesResponse(null, List.of(), List.of(), null, 0L);
        }
        return new CustomerPreferencesResponse(document.getCity(), document.getEventTypes(),
                document.getFavoriteArtists(), document.getUpdatedAt(), document.getVersion());
    }

    private static void validate(UUID customerId, CustomerPreferencesRequest request) {
        if (request == null) {
            throw ApiProblemException.invalidPreferences("El cuerpo de preferencias es obligatorio");
        }
        if (customerId == null) {
            throw ApiProblemException.identityInBody("customerId se deriva del JWT, no del cuerpo");
        }
        if (request.city() != null) {
            String city = request.city().trim();
            if (city.isEmpty()) {
                throw ApiProblemException.invalidPreferences("city no puede estar en blanco");
            }
            if (city.length() > 120) {
                throw ApiProblemException.invalidPreferences("city no puede superar los 120 caracteres");
            }
        }
        validateList(request.eventTypes(), 10, 80, "eventTypes");
        validateList(request.favoriteArtists(), 20, 200, "favoriteArtists");
    }

    private static void validateList(List<String> values, int maxItems, int maxLength, String field) {
        if (values == null) {
            return;
        }
        if (values.size() > maxItems) {
            throw ApiProblemException.invalidPreferences(
                    field + " no puede superar los " + maxItems + " elementos");
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                throw ApiProblemException.invalidPreferences(field + " no puede contener elementos vacíos");
            }
            if (value.trim().length() > maxLength) {
                throw ApiProblemException.invalidPreferences(
                        field + " no puede contener elementos de más de " + maxLength + " caracteres");
            }
            String key = value.trim().toLowerCase(Locale.ROOT);
            if (!normalized.add(key)) {
                throw ApiProblemException.invalidPreferences(
                        field + " no puede contener duplicados ignorando mayúsculas");
            }
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static List<String> trimList(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream().map(String::trim).toList();
    }
}
