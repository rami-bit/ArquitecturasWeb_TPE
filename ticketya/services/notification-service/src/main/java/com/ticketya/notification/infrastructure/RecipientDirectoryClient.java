package com.ticketya.notification.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketya.notification.application.NotificationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resuelve destinatarios con los contratos internos de otros servicios, sin
 * acceder a sus bases:
 * <ul>
 *   <li>Purchase {@code GET /internal/events/{eventId}/buyers}: compradores del evento.</li>
 *   <li>Preferences {@code GET /internal/preferences/matches}: clientes con alguno de
 *       los artistas como favorito y la misma ciudad.</li>
 * </ul>
 * Timeouts cortos; cualquier falla se informa como {@link RecipientLookupException}
 * y el disparador se reintenta más tarde.
 */
@Component
public class RecipientDirectoryClient {
    private final RestClient http;
    private final String purchaseUrl;
    private final String preferencesUrl;

    public RecipientDirectoryClient(NotificationProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.http().connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.http().readTimeoutMs()));
        this.http = RestClient.builder().requestFactory(factory).build();
        this.purchaseUrl = properties.purchase().url().replaceAll("/+$", "");
        this.preferencesUrl = properties.preferences().url().replaceAll("/+$", "");
    }

    public List<UUID> buyersOf(UUID eventId, String correlationId) {
        return fetch(purchaseUrl + "/internal/events/" + eventId + "/buyers", correlationId, "Purchase");
    }

    public List<UUID> fansInCity(String city, List<String> artists, String correlationId) {
        if (city == null || city.isBlank() || artists.isEmpty()) {
            return List.of();
        }
        UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(preferencesUrl + "/internal/preferences/matches")
                .queryParam("city", city);
        artists.forEach(artist -> uri.queryParam("artist", artist));
        return fetch(uri.encode().toUriString(), correlationId, "Preferences");
    }

    private List<UUID> fetch(String url, String correlationId, String dependency) {
        try {
            JsonNode body = http.get().uri(java.net.URI.create(url))
                    .header("X-Correlation-Id", correlationId == null ? "" : correlationId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            List<UUID> ids = new ArrayList<>();
            if (body != null) {
                body.path("customerIds").forEach(node -> ids.add(UUID.fromString(node.asText())));
            }
            return ids;
        } catch (RuntimeException failure) {
            throw new RecipientLookupException(dependency + " no disponible: " + failure.getMessage(), failure);
        }
    }
}
