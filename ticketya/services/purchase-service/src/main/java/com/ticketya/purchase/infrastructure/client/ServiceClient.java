package com.ticketya.purchase.infrastructure.client;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * Cliente base para llamadas síncronas a los servicios internos (Catalog,
 * Inventory, Payment y Ticket). Propaga {@code X-Correlation-Id} y traduce
 * fallos de red o respuestas de error en {@link ServiceException}.
 */
public abstract class ServiceClient {

    protected final RestClient http;
    protected final String baseUrl;
    protected final ObjectMapper mapper;

    protected ServiceClient(String baseUrl, ObjectMapper mapper) {
        this.baseUrl = baseUrl;
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    protected JsonNode get(String path, String correlationId) {
        try {
            String body = http.get()
                    .uri(baseUrl + path)
                    .header("X-Correlation-Id", correlationId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return readTree(body);
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            throw new ServiceException(HttpStatusCode.valueOf(exception.getStatusCode().value()),
                    "Error llamando " + baseUrl + path + ": " + exception.getStatusCode());
        } catch (RestClientException exception) {
            throw new ServiceException("Falla de conexión llamando " + baseUrl + path, exception);
        }
    }

    protected JsonNode post(String path, Object body, String correlationId) {
        try {
            String response = http.post()
                    .uri(baseUrl + path)
                    .header("X-Correlation-Id", correlationId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return readTree(response);
        } catch (HttpClientErrorException | HttpServerErrorException exception) {
            throw new ServiceException(HttpStatusCode.valueOf(exception.getStatusCode().value()),
                    "Error llamando " + baseUrl + path + ": " + exception.getStatusCode());
        } catch (RestClientException exception) {
            throw new ServiceException("Falla de conexión llamando " + baseUrl + path, exception);
        }
    }

    private JsonNode readTree(String content) {
        try {
            return mapper.readTree(content);
        } catch (JsonProcessingException exception) {
            throw new ServiceException("No se pudo parsear la respuesta JSON: " + exception.getMessage(),
                    exception);
        }
    }
}
