package com.ticketya.purchase.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Cliente interno del simulador de pagos (Payment Mock). */
public class PaymentClient extends ServiceClient {

    public PaymentClient(String baseUrl, ObjectMapper mapper) {
        super(baseUrl, mapper);
    }

    /**
     * Cobra una orden de forma idempotente. El resultado lo decide el token
     * de simulación: tok_approved, tok_rejected o tok_pending.
     */
    public JsonNode charge(UUID orderId, BigDecimal amount, String currency, String method,
                           String token, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId.toString());
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("method", method);
        body.put("token", token);
        return post("/internal/payments", body, correlationId);
    }

    public JsonNode refund(UUID paymentId, String correlationId) {
        return post("/internal/payments/" + paymentId + "/refund", Map.of(), correlationId);
    }

    public JsonNode getByOrder(UUID orderId, String correlationId) {
        return get("/internal/payments/by-order/" + orderId, correlationId);
    }
}
