package com.ticketya.catalog.api.error;

import org.springframework.http.HttpStatus;

import java.util.Objects;

public class ApiProblemException extends RuntimeException {
    private final HttpStatus status;
    private final String title;

    public ApiProblemException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = Objects.requireNonNull(status);
        this.title = Objects.requireNonNull(title);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
