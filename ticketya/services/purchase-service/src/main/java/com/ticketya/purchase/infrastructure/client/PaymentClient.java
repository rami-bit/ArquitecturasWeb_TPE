package com.ticketya.purchase.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.purchase.infrastructure.resilience.CircuitBreaker;
import org.springframework.http.HttpStatusCode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cliente interno del simulador de pagos (Payment Mock) con circuit breaker.
 * Sólo los fallos de transporte y las respuestas {@code 5xx} cuentan como
 * fallas; un rechazo de pago ({@code 4xx}) es una respuesta de negocio y no
 * abre el circuito. Con el circuito abierto se responde {@code 503} sin
 * invocar a Payment para no repetir llamadas inútiles.
 */
public class PaymentClient extends ServiceClient {

    private final CircuitBreaker breaker;

    public PaymentClient(String baseUrl, ObjectMapper mapper) {
        this(baseUrl, mapper, null);
    }

    public PaymentClient(String baseUrl, ObjectMapper mapper, CircuitBreaker breaker) {
        super(baseUrl, mapper);
        this.breaker = breaker;
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
        return guarded(() -> post("/internal/payments", body, correlationId));
    }

    public JsonNode refund(UUID paymentId, String correlationId) {
        return guarded(() -> post("/internal/payments/" + paymentId + "/refund", Map.of(), correlationId));
    }

    public JsonNode getByOrder(UUID orderId, String correlationId) {
        return guarded(() -> get("/internal/payments/by-order/" + orderId, correlationId));
    }

    private JsonNode guarded(Supplier<JsonNode> call) {
        if (breaker == null) {
            return call.get();
        }
        if (!breaker.allowsCall()) {
            throw new ServiceException(HttpStatusCode.valueOf(503),
                    "Circuit breaker " + breaker.name() + " is open; Payment no fue invocado");
        }
        try {
            JsonNode result = call.get();
            breaker.onSuccess();
            return result;
        } catch (ServiceException exception) {
            HttpStatusCode status = exception.status();
            if (status == null || status.value() >= 500) {
                breaker.onFailure();
            } else {
                // 4xx: la dependencia respondió. No abre el circuito y reinicia la racha.
                breaker.onSuccess();
            }
            throw exception;
        } catch (RuntimeException exception) {
            breaker.onFailure();
            throw exception;
        }
    }
}
