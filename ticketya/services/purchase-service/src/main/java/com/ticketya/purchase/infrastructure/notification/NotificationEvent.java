package com.ticketya.purchase.infrastructure.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de notificación publicado por Purchase. Lleva identificadores, el
 * tipo y, cuando se conocen, datos públicos del evento para el texto del
 * mensaje (PDF-F11); nunca datos de pago ni payloads QR.
 */
public record NotificationEvent(String type, UUID orderId, UUID customerId, String correlationId,
                                UUID eventId, String eventName, OffsetDateTime eventStartsAt) {

    public NotificationEvent(String type, UUID orderId, UUID customerId, String correlationId) {
        this(type, orderId, customerId, correlationId, null, null, null);
    }
}
