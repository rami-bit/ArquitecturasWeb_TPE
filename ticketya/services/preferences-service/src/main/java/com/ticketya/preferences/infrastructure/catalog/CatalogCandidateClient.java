package com.ticketya.preferences.infrastructure.catalog;

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

import java.util.List;

/**
 * Contrato interno de candidatos de Catalog. Sin reintentos: un timeout o
 * cualquier error de transporte se traduce en `503` porque sin candidatos no
 * hay nada que recomendar.
 */
@Component
public class CatalogCandidateClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogCandidateClient.class);

    private final RestTemplate rest;
    private final String baseUrl;
    private final int candidateLimit;

    public CatalogCandidateClient(PreferencesProperties properties) {
        PreferencesProperties.Catalog catalog = properties.catalog();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(catalog.connectTimeoutMs());
        factory.setReadTimeout(catalog.readTimeoutMs());
        this.rest = new RestTemplate(factory);
        this.baseUrl = trimTrailingSlash(catalog.baseUrl());
        this.candidateLimit = properties.candidateLimit();
    }

    public List<EventCandidate> candidates() {
        String url = baseUrl + "/internal/events/recommendation-candidates?limit=" + candidateLimit;
        HttpHeaders headers = new HttpHeaders();
        String correlationId = CorrelationIdFilter.current();
        if (correlationId != null) {
            headers.set(CorrelationIdFilter.HEADER_NAME, correlationId);
        }
        try {
            ResponseEntity<CatalogCandidatesResponse> response = rest.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), CatalogCandidatesResponse.class);
            CatalogCandidatesResponse body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.items() == null) {
                throw ApiProblemException.serviceUnavailable(
                        "Catalog returned an invalid recommendation candidates response");
            }
            return body.items();
        } catch (RestClientException unavailable) {
            log.warn("Catalog unavailable while fetching recommendation candidates: {}",
                    unavailable.getMessage());
            throw ApiProblemException.serviceUnavailable("Catalog service is unavailable");
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
