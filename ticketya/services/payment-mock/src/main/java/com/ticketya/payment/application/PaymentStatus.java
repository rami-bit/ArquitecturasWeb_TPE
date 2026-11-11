package com.ticketya.payment.application;

/**
 * Estado del pago simulado. {@code REFUNDED} es terminal y se alcanza por
 * reintegro de un pago {@code APPROVED}.
 */
public enum PaymentStatus {
    APPROVED,
    REJECTED,
    PENDING,
    REFUNDED
}
