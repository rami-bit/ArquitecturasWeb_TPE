package com.ticketya.preferences.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración propia del servicio: base MongoDB, contratos internos y
 * timeouts. Los timeouts se comparten entre Catalog y Purchase.
 */
@ConfigurationProperties(prefix = "preferences")
public record PreferencesProperties(
        Mongo mongodb,
        Catalog catalog,
        Purchase purchase,
        int candidateLimit) {

    public record Mongo(String uri, String database) {
    }

    public record Catalog(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
    }

    public record Purchase(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
    }
}
