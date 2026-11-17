package com.ticketya.queue.api.error;

import org.springframework.http.HttpStatus;

/**
 * Excepción de dominio que se traduce a una respuesta {@code application/problem+json}.
 * El {@code type} identifica la regla de negocio que falló (por ejemplo
 * {@code event-not-queued}) y por defecto es {@code about:blank}.
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

    public static ApiProblemException notFound(String detail) {
        return new ApiProblemException(HttpStatus.NOT_FOUND, "Not found", detail, "about:blank");
    }

    public static ApiProblemException serviceUnavailable(String detail) {
        return new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                detail, "about:blank");
    }

    /** 404: no existe ningún turno para (evento, cliente). */
    public static ApiProblemException entryNotFound(String detail) {
        return of(HttpStatus.NOT_FOUND, "entry-not-found", "Queue entry not found", detail);
    }

    /** 409: el evento está publicado pero no es de alta demanda. */
    public static ApiProblemException eventNotQueued(String detail) {
        return of(HttpStatus.CONFLICT, "event-not-queued", "Event not queued", detail);
    }

    /** 409: el evento dejó de estar publicado; la fila quedó cerrada. */
    public static ApiProblemException queueClosed(String detail) {
        return of(HttpStatus.CONFLICT, "queue-closed", "Queue closed", detail);
    }

    /** 403: el turno no es admisible o el token no coincide / está vencido. */
    public static ApiProblemException admissionRejected(String detail) {
        return of(HttpStatus.FORBIDDEN, "admission-rejected", "Admission rejected", detail);
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
