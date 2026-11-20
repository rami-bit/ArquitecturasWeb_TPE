package com.ticketya.preferences.infrastructure.purchase;

import com.ticketya.preferences.api.error.ApiProblemException;
import com.ticketya.preferences.api.tracing.CorrelationIdFilter;
import com.ticketya.preferences.config.PreferencesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Set;
import java.util.UUID;

/**
 * Contrato interno de señales de historial de Purchase. Sin reintentos: un
 * fallo deja el historial vacío para que Recommendations degrade con
 * `HISTORY_UNAVAILABLE` en lugar de fallar la consulta.
 */
@Component
public class PurchaseSignalsClient {

    private static final Logger log = LoggerFactory.getLogger(PurchaseSignalsClient.class);

    private final RestTemplate rest;
    private final String baseUrl;

    public PurchaseSignalsClient(PreferencesProperties properties) {
        PreferencesProperties.Purchase purchase = properties.purchase();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(purchase.connectTimeoutMs());
        factory.setReadTimeout(purchase.readTimeoutMs());
        this.rest = new RestTemplate(factory);
        this.baseUrl = trimTrailingSlash(purchase.baseUrl());
    }

    public RecommendationSignals fetch(UUID customerId) {
        String url = baseUrl + "/internal/customers/" + customerId + "/recommendation-signals";
        HttpHeaders headers = new HttpHeaders();
        String correlationId = CorrelationIdFilter.current();
        if (correlationId != null) {
            headers.set(CorrelationIdFilter.HEADER_NAME, correlationId);
        }
        try {
            ResponseEntity<RecommendationSignals> response = rest.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), RecommendationSignals.class);
            RecommendationSignals body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw ApiProblemException.serviceUnavailable(
                        "Purchase returned an invalid recommendation signals response");
            }
            return new RecommendationSignals(body.customerId(),
                    body.purchasedEventIds() == null ? Set.of() : body.purchasedEventIds(),
                    body.coPurchases() == null ? java.util.List.of() : body.coPurchases(),
                    body.generatedAt());
        } catch (RestClientException unavailable) {
            log.warn("Purchase unavailable while fetching recommendation signals for {}: {}",
                    customerId, unavailable.getMessage());
            throw ApiProblemException.serviceUnavailable("Purchase service is unavailable");
        }
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
