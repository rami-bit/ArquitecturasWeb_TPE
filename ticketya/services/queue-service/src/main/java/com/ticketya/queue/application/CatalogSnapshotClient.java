package com.ticketya.queue.application;

import com.ticketya.queue.api.error.ApiProblemException;
import com.ticketya.queue.api.tracing.CorrelationIdFilter;
import com.ticketya.queue.config.QueueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Snapshot interno de Catalog ({@code GET /internal/events/{eventId}/snapshot}).
 * Un 404 significa que el evento no existe o no está publicado y se devuelve
 * vacío para que cada endpoint decida el código (join 404, poll 409 queue-closed).
 * Fallos de transporte, timeouts y 5xx responden 503.
 */
@Component
public class CatalogSnapshotClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogSnapshotClient.class);

    private final RestTemplate rest;
    private final String baseUrl;

    public CatalogSnapshotClient(QueueProperties properties) {
        QueueProperties.Catalog catalog = properties.getCatalog();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(catalog.getConnectTimeoutMs());
        factory.setReadTimeout(catalog.getReadTimeoutMs());
        this.rest = new RestTemplate(factory);
        this.baseUrl = trimTrailingSlash(catalog.getBaseUrl());
    }

    /** Vacío cuando Catalog responde 404 (evento inexistente o no publicado). */
    public Optional<CatalogSnapshot> fetch(UUID eventId) {
        String url = baseUrl + "/internal/events/" + eventId + "/snapshot";
        HttpHeaders headers = new HttpHeaders();
        String correlationId = CorrelationIdFilter.current();
        if (correlationId != null) {
            headers.set(CorrelationIdFilter.HEADER_NAME, correlationId);
        }
        try {
            ResponseEntity<CatalogSnapshot> response = rest.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), CatalogSnapshot.class);
            CatalogSnapshot body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw ApiProblemException.serviceUnavailable(
                        "Catalog returned an invalid snapshot response");
            }
            return Optional.of(body);
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (HttpClientErrorException clientError) {
            log.warn("Catalog rejected snapshot request for event {} with status {}",
                    eventId, clientError.getStatusCode());
            throw ApiProblemException.serviceUnavailable(
                    "Catalog rejected the snapshot request");
        } catch (RestClientException unavailable) {
            log.warn("Catalog is unavailable while fetching snapshot for event {}: {}",
                    eventId, unavailable.getMessage());
            throw ApiProblemException.serviceUnavailable(
                    "Catalog service is unavailable while attending the queue");
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
