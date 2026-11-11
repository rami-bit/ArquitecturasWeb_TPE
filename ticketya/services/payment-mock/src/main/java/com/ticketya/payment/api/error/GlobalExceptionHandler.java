package com.ticketya.payment.api.error;

import com.ticketya.payment.api.tracing.CorrelationIdFilter;
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
    public ResponseEntity<Problem> domain(ApiProblemException exception, HttpServletRequest request) {
        return problem(exception.getStatus(), exception.getTitle(), exception.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<Problem> invalid(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "The request payload is invalid", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Problem> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", "The requested resource does not exist", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> unexpected(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred", request);
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String title, String detail,
                                            HttpServletRequest request) {
        Problem body = new Problem(URI.create("about:blank"), title, status.value(), detail,
                URI.create(request.getRequestURI()), CorrelationIdFilter.get(request));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
