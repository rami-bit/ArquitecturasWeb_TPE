package com.ticketya.queue.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Batch promotion and admission window settings for the virtual queue.
 * The admission secret is shared with the caller that validates tokens, so
 * a token issued here can be verified there without a round trip.
 */
@Validated
@ConfigurationProperties(prefix = "queue")
public class QueueProperties {

    @Min(1)
    private int batchSize = 10;

    @Min(1)
    private int batchIntervalSeconds = 15;

    @Min(1)
    private int admissionTtlSeconds = 120;

    private String admissionSecret = "local-development-admission-secret";

    private final Catalog catalog = new Catalog();

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getBatchIntervalSeconds() {
        return batchIntervalSeconds;
    }

    public void setBatchIntervalSeconds(int batchIntervalSeconds) {
        this.batchIntervalSeconds = batchIntervalSeconds;
    }

    public int getAdmissionTtlSeconds() {
        return admissionTtlSeconds;
    }

    public void setAdmissionTtlSeconds(int admissionTtlSeconds) {
        this.admissionTtlSeconds = admissionTtlSeconds;
    }

    public String getAdmissionSecret() {
        return admissionSecret;
    }

    public void setAdmissionSecret(String admissionSecret) {
        this.admissionSecret = admissionSecret;
    }

    public Catalog getCatalog() {
        return catalog;
    }

    public static class Catalog {
        private String baseUrl = "http://localhost:8081";
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 5000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }
}
