package com.ticketya.purchase.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cliente interno de los comandos de reserva de Inventory. */
public class InventoryClient extends ServiceClient {

    public InventoryClient(String baseUrl, ObjectMapper mapper) {
        super(baseUrl, mapper);
    }

    public JsonNode getAvailability(UUID eventId, String correlationId) {
        return get("/api/inventory/events/" + eventId + "/availability", correlationId);
    }

    /**
     * Crea una reserva temporal. Devuelve el JSON de la reserva con
     * {@code reservationId}, {@code expiresAt} y los ítems enriquecidos.
     */
    public JsonNode reserve(UUID orderId, UUID eventId, int ttlSeconds,
                            List<Map<String, Object>> items, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId.toString());
        body.put("eventId", eventId.toString());
        body.put("ttlSeconds", ttlSeconds);
        body.put("items", items);
        return post("/internal/reservations", body, correlationId);
    }

    public JsonNode confirm(UUID reservationId, String correlationId) {
        return post("/internal/reservations/" + reservationId + "/confirm", Map.of(), correlationId);
    }

    public JsonNode release(UUID reservationId, String correlationId) {
        return post("/internal/reservations/" + reservationId + "/release", Map.of(), correlationId);
    }
}
