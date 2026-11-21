package com.ticketya.notification.infrastructure;

/** Una dependencia (Purchase o Preferences) no pudo resolver los destinatarios. */
public class RecipientLookupException extends RuntimeException {
    public RecipientLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
