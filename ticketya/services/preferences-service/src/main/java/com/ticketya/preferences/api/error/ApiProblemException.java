package com.ticketya.preferences.api.error;

import org.springframework.http.HttpStatus;

/**
 * Excepción de dominio que se traduce a una respuesta {@code application/problem+json}.
 */
public class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final String type;

    public ApiProblemException(HttpStatus status, String title, String detail, String type) {
        super(detail);
        this.status = status;
        this.title = title;
        this.type = type;
    }

    public static ApiProblemException of(HttpStatus status, String type, String title, String detail) {
        return new ApiProblemException(status, title, detail, type);
    }

    public static ApiProblemException badRequest(String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "Invalid request", detail, "about:blank");
    }

    public static ApiProblemException forbidden(String detail) {
        return new ApiProblemException(HttpStatus.FORBIDDEN, "Forbidden", detail, "about:blank");
    }

    public static ApiProblemException serviceUnavailable(String detail) {
        return new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                detail, "about:blank");
    }

    /** 400: el cuerpo de preferencias incluye un campo que debe derivarse del JWT. */
    public static ApiProblemException identityInBody(String detail) {
        return of(HttpStatus.BAD_REQUEST, "customer-identity-in-body",
                "Invalid preferences payload", detail);
    }

    /** 400: las preferencias violan las reglas de D14. */
    public static ApiProblemException invalidPreferences(String detail) {
        return of(HttpStatus.BAD_REQUEST, "invalid-preferences",
                "Invalid preferences", detail);
    }

    /** 400: el límite de recomendaciones está fuera de rango. */
    public static ApiProblemException invalidLimit(String detail) {
        return of(HttpStatus.BAD_REQUEST, "invalid-limit",
                "Invalid recommendation limit", detail);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }
}
