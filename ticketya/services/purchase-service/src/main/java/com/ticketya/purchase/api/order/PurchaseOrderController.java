package com.ticketya.purchase.api.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.purchase.api.error.ApiProblemException;
import com.ticketya.purchase.application.PurchaseService;
import com.ticketya.purchase.infrastructure.audit.AuditEvent;
import com.ticketya.purchase.infrastructure.audit.AuditPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * API pública de órdenes de compra: {@code POST /api/orders},
 * {@code GET /api/orders}, {@code GET /api/orders/{orderId}} y
 * {@code POST /api/orders/{orderId}/reconcile}.
 *
 * Identifica al comprador mediante {@code X-User-Id} y opcionalmente el rol
 * mediante {@code X-User-Role}. Todas las respuestas llevan {@code X-Correlation-Id}
 * (establecido por el filtro de trazabilidad).
 *
 * <p>E2-02: el resultado de cada compra y de cada reconciliación (confirmada,
 * pendiente, rechazada, compensada o fallida) se registra en la bitácora
 * centralizada con el mismo {@code X-Correlation-Id} que recorre la saga.
 */
@RestController
@RequestMapping("/api/orders")
public class PurchaseOrderController {

    private final PurchaseService service;
    private final AuditPublisher audit;
    private final ObjectMapper mapper;

    public PurchaseOrderController(PurchaseService service, AuditPublisher audit, ObjectMapper mapper) {
        this.service = service;
        this.audit = audit;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<JsonNode> createOrder(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Admission-Token", required = false) String admissionTokenHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody(required = false) String rawBody) {
        UUID customerId = resolveUserId(userIdHeader); // 403 si falta o no es UUID
        String role = resolveRole(roleHeader); // 403 si rol inválido
        validateIdempotencyKey(idempotencyKey); // 400 si falta o excede 120
        String admissionToken = admissionTokenHeader;
        if (rawBody == null || rawBody.isBlank()) {
            throw new com.ticketya.purchase.api.error.ApiProblemException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid request", "Cuerpo de solicitud obligatorio");
        }
        String trace = resolveCorrelationId(correlationId);
        try {
            ResponseEntity<JsonNode> response = service.createOrder(customerId, role, idempotencyKey, admissionToken, trace, rawBody);
            auditSuccess("PURCHASE_ORDER", customerId, role, trace, response, idempotencyKey);
            return response;
        } catch (ApiProblemException problem) {
            auditFailure("PURCHASE_ORDER", customerId, role, trace, problem, eventIdOf(rawBody), null);
            throw problem;
        } catch (RuntimeException unexpected) {
            auditError("PURCHASE_ORDER", customerId, role, trace, unexpected, null);
            throw unexpected;
        }
    }

    @GetMapping
    public ResponseEntity<JsonNode> listOrders(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(value = "status", required = false) String status,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        UUID customerId = resolveUserId(userIdHeader);
        String role = resolveRole(roleHeader);
        return service.listOrders(customerId, role, page, size, status,
                resolveCorrelationId(correlationId));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<JsonNode> getOrder(
            @PathVariable(value = "orderId") UUID orderId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        UUID customerId = resolveUserId(userIdHeader);
        String role = resolveRole(roleHeader);
        return service.getOrder(orderId, customerId, role, resolveCorrelationId(correlationId));
    }

    @PostMapping("/{orderId}/reconcile")
    public ResponseEntity<JsonNode> reconcileOrder(
            @PathVariable(value = "orderId") UUID orderId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        UUID customerId = resolveUserId(userIdHeader);
        String role = resolveRole(roleHeader);
        String trace = resolveCorrelationId(correlationId);
        try {
            ResponseEntity<JsonNode> response = service.reconcileOrder(orderId, customerId, role, trace);
            auditSuccess("PURCHASE_RECONCILE", customerId, role, trace, response, null);
            return response;
        } catch (ApiProblemException problem) {
            auditFailure("PURCHASE_RECONCILE", customerId, role, trace, problem, null, orderId);
            throw problem;
        } catch (RuntimeException unexpected) {
            auditError("PURCHASE_RECONCILE", customerId, role, trace, unexpected, orderId);
            throw unexpected;
        }
    }

    // ------------------------------------------------------------------ bitácora (E2-02)

    private void auditSuccess(String operation, UUID customerId, String role, String trace,
                              ResponseEntity<JsonNode> response, String idempotencyKey) {
        JsonNode body = response.getBody();
        if (body == null) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("httpStatus", response.getStatusCode().value());
        details.put("eventId", body.path("eventId").asText(null));
        details.put("totalAmount", body.path("totalAmount").asDouble());
        details.put("paymentStatus", body.path("payment").path("status").asText(null));
        int tickets = 0;
        for (JsonNode item : body.path("items")) {
            tickets += item.path("tickets").size();
        }
        details.put("ticketCount", tickets);
        if (idempotencyKey != null) {
            details.put("idempotencyKey", idempotencyKey);
            details.put("replay", response.getStatusCode().value() == 200);
        }
        audit.publish(AuditEvent.of(trace, operation, body.path("status").asText("UNKNOWN"),
                customerId.toString(), role, "ORDER", body.path("orderId").asText(null), details));
    }

    private void auditFailure(String operation, UUID customerId, String role, String trace,
                              ApiProblemException problem, String eventId, UUID orderId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("httpStatus", problem.getStatus().value());
        details.put("detail", problem.getMessage());
        if (eventId != null) {
            details.put("eventId", eventId);
        }
        UUID resource = problem.getOrderId() != null ? problem.getOrderId() : orderId;
        String outcome = problem.getOrderStatus() != null ? problem.getOrderStatus() : "FAILED";
        audit.publish(AuditEvent.of(trace, operation, outcome, customerId.toString(), role, "ORDER",
                resource, details));
    }

    private void auditError(String operation, UUID customerId, String role, String trace,
                            RuntimeException error, UUID orderId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("httpStatus", 500);
        details.put("error", error.getClass().getSimpleName());
        audit.publish(AuditEvent.of(trace, operation, "ERROR", customerId.toString(), role, "ORDER",
                orderId, details));
    }

    private String eventIdOf(String rawBody) {
        try {
            JsonNode node = mapper.readTree(rawBody).path("eventId");
            return node.isTextual() ? node.asText() : null;
        } catch (Exception unreadable) {
            return null;
        }
    }

    // ------------------------------------------------------------------ validación de headers

    private UUID resolveUserId(String header) {
        if (header == null || header.isBlank()) {
            throw new com.ticketya.purchase.api.error.ApiProblemException(
                    HttpStatus.FORBIDDEN, "Forbidden", "X-User-Id es obligatorio");
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            throw new com.ticketya.purchase.api.error.ApiProblemException(
                    HttpStatus.FORBIDDEN, "Forbidden", "X-User-Id debe ser un UUID válido");
        }
    }

    private String resolveRole(String header) {
        if (header == null || header.isBlank()) {
            return "CLIENT";
        }
        String role = header.trim().toUpperCase();
        if (!"CLIENT".equals(role) && !"ADMIN".equals(role)) {
            throw new com.ticketya.purchase.api.error.ApiProblemException(
                    HttpStatus.FORBIDDEN, "Forbidden", "Rol inválido: " + header);
        }
        return role;
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new com.ticketya.purchase.api.error.ApiProblemException(
                    HttpStatus.BAD_REQUEST, "Invalid request", "Idempotency-Key es obligatorio");
        }
        if (key.length() > 120) {
            throw new com.ticketya.purchase.api.error.ApiProblemException(
                    HttpStatus.BAD_REQUEST, "Invalid request", "Idempotency-Key excede los 120 caracteres");
        }
    }

    private String resolveCorrelationId(String header) {
        return header == null || header.isBlank() ? null : header.trim();
    }
}
