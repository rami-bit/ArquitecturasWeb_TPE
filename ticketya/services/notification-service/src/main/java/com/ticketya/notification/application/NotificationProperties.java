package com.ticketya.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Configuración de {@code notifications.*} (ver application.yml). */
@ConfigurationProperties("notifications")
public record NotificationProperties(
        List<String> channels,
        Smtp smtp,
        Endpoint purchase,
        Endpoint preferences,
        Http http,
        Scheduler scheduler,
        Retry retry,
        Provider provider) {

    public record Endpoint(String url) {
    }

    public record Smtp(String from) {
    }

    public record Http(int connectTimeoutMs, int readTimeoutMs) {
    }

    public record Scheduler(boolean enabled, long delayMs, int batchSize) {
    }

    public record Retry(int maxAttempts, long baseDelayMs, long maxDelayMs, int leaseSeconds) {
    }

    public record Provider(String mode) {
    }
}
