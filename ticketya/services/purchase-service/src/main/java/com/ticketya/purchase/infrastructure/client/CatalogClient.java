package com.ticketya.purchase.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

/** Cliente interno del snapshot de compra de Catalog (con precios por sector). */
public class CatalogClient extends ServiceClient {

    public CatalogClient(String baseUrl, ObjectMapper mapper) {
        super(baseUrl, mapper);
    }

    public JsonNode getPurchaseSnapshot(UUID eventId, String correlationId) {
        return get("/internal/events/" + eventId + "/purchase-snapshot", correlationId);
    }
}
