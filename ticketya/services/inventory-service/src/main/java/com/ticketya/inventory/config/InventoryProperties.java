package com.ticketya.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inventory")
public class InventoryProperties {
    private final Catalog catalog = new Catalog();
    private int holdTtlSeconds = 600;

    public Catalog getCatalog() {
        return catalog;
    }

    public int getHoldTtlSeconds() {
        return holdTtlSeconds;
    }

    public void setHoldTtlSeconds(int holdTtlSeconds) {
        this.holdTtlSeconds = holdTtlSeconds;
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
