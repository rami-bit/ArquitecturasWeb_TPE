package com.ticketya.purchase.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ticketya.purchase.api.tracing.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Traduce las excepciones de compra a respuestas {@code application/problem+json}
 * que siempre incluyen el {@code correlationId} de la solicitud.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper mapper;

    public GlobalExceptionHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ObjectNode> handleApiProblem(ApiProblemException exception, HttpServletRequest request) {
        return problem(exception.getStatus(), exception.getTitle(), exception.getMessage(),
                exception.getOrderId(), exception.getOrderStatus(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ObjectNode> handleUnreadableBody(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "Cuerpo de solicitud inválido o no es JSON", null, null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ObjectNode> handleUnexpected(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "Error inesperado procesando la solicitud", null, null, request);
    }

    private ResponseEntity<ObjectNode> problem(HttpStatus status, String title, String detail,
                                               UUID orderId, String orderStatus, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.get(request);
        ObjectNode node = mapper.createObjectNode();
        node.put("type", "about:blank");
        node.put("title", title);
        node.put("status", status.value());
        node.put("detail", detail == null || detail.isBlank() ? title : detail);
        node.put("instance", request.getRequestURI());
        node.put("correlationId", correlationId == null || correlationId.isBlank() ? "" : correlationId);
        if (orderId != null) {
            node.put("orderId", orderId.toString());
        }
        if (orderStatus != null) {
            node.put("orderStatus", orderStatus);
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(node);
    }
}
