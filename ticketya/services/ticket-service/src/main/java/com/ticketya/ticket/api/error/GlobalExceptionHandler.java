package com.ticketya.ticket.api.error;

import com.ticketya.ticket.api.tracing.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<Problem> handleApiProblem(
            ApiProblemException exception, HttpServletRequest request) {
        return problem(exception.getStatus(), exception.getTitle(), exception.getMessage(), request);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Problem> handleInvalidRequest(
            Exception exception, HttpServletRequest request) {
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = "The request payload is invalid";
        }
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Problem> handleResourceNotFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found",
                exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred", request);
    }

    private ResponseEntity<Problem> problem(
            HttpStatus status, String title, String detail, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.get(request);
        Problem body = new Problem(
                URI.create("about:blank"),
                title,
                status.value(),
                detail == null || detail.isBlank() ? title : detail,
                URI.create(request.getRequestURI()),
                correlationId);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
