package com.ticketya.catalog.application.event;

import com.ticketya.catalog.api.error.ApiProblemException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
public class InventoryCancellationClient {
    private final RestTemplate rest;
    private final String baseUrl;

    public InventoryCancellationClient(
            @Value("${inventory.base-url:http://localhost:8082}") String baseUrl,
            @Value("${inventory.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${inventory.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.rest = new RestTemplate(factory);
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public void notifyCancelled(UUID eventId) {
        try {
            rest.exchange(baseUrl + "/internal/events/" + eventId + "/cancellation",
                    HttpMethod.PUT, HttpEntity.EMPTY, Void.class);
        } catch (RestClientException ex) {
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory unavailable",
                    "The cancellation could not be notified to Inventory; retry the request");
        }
    }
}
