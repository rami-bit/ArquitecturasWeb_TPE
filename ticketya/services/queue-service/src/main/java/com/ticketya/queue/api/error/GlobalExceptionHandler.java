package com.ticketya.queue.api.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ticketya.queue.api.tracing.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce las excepciones de la fila virtual a respuestas
 * {@code application/problem+json} que siempre incluyen el
 * {@code correlationId} de la solicitud.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectMapper mapper;

    public GlobalExceptionHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ObjectNode> handleApiProblem(
            ApiProblemException exception, HttpServletRequest request) {
        return problem(exception.getStatus(), exception.getTitle(),
                exception.getMessage(), exception.getType(), request);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ObjectNode> handleInvalidRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "The request payload is invalid", "about:blank", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ObjectNode> handleResourceNotFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found",
                "The requested resource does not exist", "about:blank", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ObjectNode> handleResponseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return problem(status, status.getReasonPhrase(), exception.getReason(),
                "about:blank", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ObjectNode> handleUnexpected(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred", "about:blank", request);
    }

    private ResponseEntity<ObjectNode> problem(HttpStatus status, String title, String detail,
                                               String type, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.get(request);
        ObjectNode node = mapper.createObjectNode();
        node.put("type", type == null || type.isBlank() ? "about:blank" : type);
        node.put("title", title);
        node.put("status", status.value());
        node.put("detail", detail == null || detail.isBlank() ? title : detail);
        node.put("instance", request.getRequestURI());
        node.put("correlationId", correlationId == null || correlationId.isBlank() ? "" : correlationId);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(node);
    }
}
