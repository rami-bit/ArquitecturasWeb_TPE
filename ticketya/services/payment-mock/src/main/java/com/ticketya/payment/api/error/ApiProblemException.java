package com.ticketya.payment.api.error;

import org.springframework.http.HttpStatus;

/**
 * Excepcion de control que se traduce en una respuesta {@code
 * application/problem+json} con el estado y t\u00edtulo informados.
 */
public class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public ApiProblemException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public static ApiProblemException badRequest(String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    public static ApiProblemException conflict(String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, "Conflict", detail);
    }

    public static ApiProblemException notFound(String detail) {
        return new ApiProblemException(HttpStatus.NOT_FOUND, "Not found", detail);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
