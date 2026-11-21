package com.ticketya.preferences.application.notification;

import com.ticketya.preferences.api.error.ApiProblemException;
import com.ticketya.preferences.domain.preferences.CustomerPreferences;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * PDF-F11: clientes a avisar cuando un artista favorito publica un evento en su
 * ciudad. Comparación exacta, insensible a mayúsculas y espacios extremos, como
 * el scorer de recomendaciones. Sólo devuelve identificadores de cliente.
 */
@Service
public class PreferenceMatchService {
    static final String COLLECTION = "customer_preferences";
    static final int MAX_ARTISTS = 50;
    static final int MAX_RESULTS = 5000;

    private final MongoTemplate mongoTemplate;

    public PreferenceMatchService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<UUID> fansInCity(String city, List<String> artists) {
        String normalizedCity = city == null ? "" : city.trim();
        List<String> names = artists == null ? List.of()
                : artists.stream().filter(a -> a != null && !a.isBlank()).map(String::trim).distinct().toList();
        if (normalizedCity.isEmpty() || names.isEmpty()) {
            return List.of();
        }
        if (normalizedCity.length() > 120 || names.size() > MAX_ARTISTS
                || names.stream().anyMatch(name -> name.length() > 200)) {
            throw ApiProblemException.badRequest("city <= 120 caracteres y hasta 50 artistas de <= 200 caracteres");
        }
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("city").regex(exact(normalizedCity)),
                Criteria.where("favoriteArtists").in(names.stream().map(PreferenceMatchService::exact).toList())))
                .limit(MAX_RESULTS);
        query.fields().include("_id");
        return mongoTemplate.find(query, CustomerPreferences.class, COLLECTION).stream()
                .map(document -> UUID.fromString(document.getId()))
                .toList();
    }

    private static Pattern exact(String value) {
        return Pattern.compile("^\\s*" + Pattern.quote(value) + "\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
