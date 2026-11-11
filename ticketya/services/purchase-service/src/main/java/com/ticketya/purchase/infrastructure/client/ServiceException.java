package com.ticketya.purchase.infrastructure.client;

import org.springframework.http.HttpStatusCode;

public class ServiceException extends RuntimeException {

    private final HttpStatusCode status;

    public ServiceException(HttpStatusCode status, String message) {
        super(message);
        this.status = status;
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
        this.status = null;
    }

    public HttpStatusCode status() {
        return status;
    }
}
