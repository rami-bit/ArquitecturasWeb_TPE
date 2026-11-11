package com.ticketya.purchase.domain.model;

/**
 * Estados de la orden de compra.
 * {@code CONFIRMED}, {@code REJECTED} y {@code COMPENSATED} son terminales;
 * {@code PAYMENT_PENDING} y {@code PAID_PENDING_TICKETS} son reconciliables.
 */
public enum OrderStatus {
    CONFIRMED,
    REJECTED,
    PAYMENT_PENDING,
    PAID_PENDING_TICKETS,
    COMPENSATED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == REJECTED || this == COMPENSATED;
    }

    public boolean isReconcilable() {
        return this == PAYMENT_PENDING || this == PAID_PENDING_TICKETS;
    }

    public static boolean isValid(String status) {
        try {
            valueOf(status);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
