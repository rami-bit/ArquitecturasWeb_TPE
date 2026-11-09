package com.ticketya.ticket.api.error;

import org.springframework.http.HttpStatus;

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

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
