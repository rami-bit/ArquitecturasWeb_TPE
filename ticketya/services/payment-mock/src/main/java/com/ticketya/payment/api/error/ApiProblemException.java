package com.ticketya.payment.api.error;

import org.springframework.http.HttpStatus;

public class ApiProblemException extends RuntimeException {
    private final HttpStatus status;
    private final String title;

    public ApiProblemException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus getStatus() { return status; }
    public String getTitle() { return title; }
}
