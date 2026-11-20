package com.ticketya.purchase.infrastructure.notification;

import java.util.UUID;

/**
 * Evento de notificación publicado por Purchase. Sólo lleva identificadores y
 * el tipo: nunca datos de pago ni payloads QR.
 */
public record NotificationEvent(String type, UUID orderId, UUID customerId, String correlationId) {
}
