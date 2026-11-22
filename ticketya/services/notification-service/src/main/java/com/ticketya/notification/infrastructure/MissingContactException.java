package com.ticketya.notification.infrastructure;

/** An EMAIL notification without a registered address cannot be delivered. */
public class MissingContactException extends RuntimeException {
}
