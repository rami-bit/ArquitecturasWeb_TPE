package com.ticketya.purchase.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cliente interno del comando de emisión de tickets de Ticket Service. */
public class TicketClient extends ServiceClient {

    public TicketClient(String baseUrl, ObjectMapper mapper) {
        super(baseUrl, mapper);
    }

    /**
     * Emite tickets idempotentes para un ítem de orden (clave: orderId + orderItemId).
     */
    public JsonNode issue(UUID orderId, UUID orderItemId, Map<String, Object> body, String correlationId) {
        return post("/internal/orders/" + orderId + "/items/" + orderItemId + "/tickets", body, correlationId);
    }
}
