package com.ticketya.ticket.application.access;

import org.springframework.http.HttpStatus;

/** Resultado de un intento de ingreso y el código HTTP con que se informa. */
public enum AccessResult {
    ADMITTED(HttpStatus.OK, "Entrada válida: ingreso registrado"),
    ALREADY_USED(HttpStatus.CONFLICT, "La entrada ya fue utilizada"),
    REVOKED(HttpStatus.CONFLICT, "La entrada fue revocada (devolución o cancelación)"),
    WRONG_EVENT(HttpStatus.UNPROCESSABLE_ENTITY, "La entrada corresponde a otro evento"),
    UNKNOWN_TICKET(HttpStatus.NOT_FOUND, "QR inválido o entrada inexistente");

    private final HttpStatus status;
    private final String description;

    AccessResult(HttpStatus status, String description) {
        this.status = status;
        this.description = description;
    }

    public HttpStatus status() {
        return status;
    }

    public String description() {
        return description;
    }
}
