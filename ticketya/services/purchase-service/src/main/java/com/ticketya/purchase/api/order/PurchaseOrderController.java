package com.ticketya.purchase.api.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketya.purchase.application.PurchaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API pública de órdenes de compra: {@code POST /api/orders},
 * {@code GET /api/orders}, {@code GET /api/orders/{orderId}} y
 * {@code POST /api/orders/{orderId}/reconcile}.
 *
 * Identifica al comprador mediante {@code X-User-Id} y opcionalmente el rol
 * mediante {@code X-User-Role}. Todas las respuestas llevan {@code X-Correlation-Id}
 * (establecido por el filtro de trazabilidad).
 */
@RestController
@RequestMapping("/api/orders")
public class PurchaseOrderController {

    private final PurchaseService service;

    public PurchaseOrderController(PurchaseService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<JsonNode> createOrder(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody(required = false) String rawBody) {
        UUID customerId = resolveUserId(userIdHeader); // 403 si falta o no es UUID
        String role = resolveRole(roleHeader); // 403 si rol inválido
        validateIdempotencyKey(idempotencyKey); // 400 si falta o excede 120
        if (rawBody == null || rawBody.isBlank()) {
            throw new com.ticketya.purchase.api.error.ApiProblemException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid request", "Cuerpo de solicitud obligatorio");
        }
        return service.createOrder(customerId, role, idempotencyKey,
                resolveCorrelationId(correlationId), rawBody);
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
        return service.reconcileOrder(orderId, customerId, role, resolveCorrelationId(correlationId));
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
