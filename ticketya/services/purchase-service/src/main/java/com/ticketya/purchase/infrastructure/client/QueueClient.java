package com.ticketya.purchase.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cliente interno de la fila virtual. Purchase sólo lo invoca cuando el snapshot
 * de compra informa un evento de alta demanda: valida y consume el token de
 * admisión en una sola operación atómica dentro de la fila.
 */
public class QueueClient extends ServiceClient {

    public QueueClient(String baseUrl, ObjectMapper mapper) {
        super(baseUrl, mapper);
    }

    public JsonNode validateAdmission(UUID eventId, UUID clientId, String token,
                                      String idempotencyKey, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId.toString());
        body.put("clientId", clientId.toString());
        body.put("token", token);
        if (idempotencyKey != null) {
            body.put("idempotencyKey", idempotencyKey);
        }
        if (correlationId != null) {
            body.put("correlationId", correlationId);
        }
        return post("/internal/admissions/validate", body, correlationId);
    }
}
