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

    /**
     * Detalle de gestión del evento con la identidad del solicitante. Catalog
     * aplica sus reglas de ownership: ORGANIZER sólo accede a sus eventos
     * ({@code 403} si es ajeno) y ADMIN a cualquiera.
     */
    public JsonNode getManagedEvent(UUID eventId, UUID userId, String role, String correlationId) {
        return get("/api/management/events/" + eventId, correlationId,
                java.util.Map.of("X-User-Id", userId.toString(), "X-User-Role", role));
    }
}
