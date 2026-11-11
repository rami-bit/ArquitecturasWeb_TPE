package com.ticketya.purchase.api.error;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Excepción de dominio que se traduce a una respuesta {@code application/problem+json}.
 * Puede incluir {@code orderId} y {@code orderStatus} como contexto opcional del problema.
 */
public class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final UUID orderId;
    private final String orderStatus;

    public ApiProblemException(HttpStatus status, String title, String detail) {
        this(status, title, detail, null, null);
    }

    public ApiProblemException(HttpStatus status, String title, String detail, UUID orderId, String orderStatus) {
        super(detail);
        this.status = status;
        this.title = title;
        this.orderId = orderId;
        this.orderStatus = orderStatus;
    }

    public static ApiProblemException badRequest(String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    public static ApiProblemException forbidden(String detail) {
        return new ApiProblemException(HttpStatus.FORBIDDEN, "Forbidden", detail);
    }

    public static ApiProblemException notFound(String detail) {
        return new ApiProblemException(HttpStatus.NOT_FOUND, "Not found", detail);
    }

    public static ApiProblemException conflict(String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, "Conflict", detail);
    }

    /** 402: pago rechazado; la orden queda REJECTED. */
    public static ApiProblemException paymentRequired(UUID orderId, String detail) {
        return new ApiProblemException(HttpStatus.PAYMENT_REQUIRED, "Payment required", detail, orderId, "REJECTED");
    }

    /** 409: orden compensada tras reintegro y liberación de inventario. */
    public static ApiProblemException compensated(UUID orderId, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, "Conflict", detail, orderId, "COMPENSATED");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getOrderStatus() {
        return orderStatus;
    }
}
