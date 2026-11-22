package com.ticketya.notification.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Textos de cada notificación. Nunca incluyen datos de pago ni el payload del QR. */
public final class MessageTemplates {
    private static final int MAX_SUBJECT = 200;

    private MessageTemplates() {
    }

    public record Message(String subject, String body) {
    }

    public static Message render(TriggerType type, JsonNode payload) {
        String eventName = text(payload, "eventName", "tu evento");
        String startsAt = text(payload, "startsAt", text(payload, "eventStartsAt", null));
        String when = startsAt == null ? "" : " (" + startsAt + ")";
        return switch (type) {
            case ORDER_CONFIRMED -> new Message(
                    limit("Compra confirmada: " + eventName),
                    "Tu orden " + text(payload, "orderId", "") + " fue confirmada" + when
                            + ". Tus entradas con QR ya están disponibles en la app.");
            case ORDER_CANCELLED -> new Message(
                    limit("Tu compra no pudo completarse"),
                    "La orden " + text(payload, "orderId", "")
                            + " fue cancelada y el pago se reintegró al medio original.");
            case EVENT_PUBLISHED -> new Message(
                    limit("Nuevo evento de un artista favorito en " + text(payload, "city", "tu ciudad")),
                    eventName + " con " + String.join(", ", participants(payload)) + when
                            + ". Las entradas ya están a la venta.");
            case EVENT_SUSPENDED -> new Message(
                    limit("Evento suspendido: " + eventName),
                    eventName + " quedó suspendido. Te avisaremos cuando se reanude; tus entradas siguen vigentes.");
            case EVENT_RESUMED -> new Message(
                    limit("Evento reanudado: " + eventName),
                    eventName + " vuelve a estar confirmado" + when + ". Tus entradas siguen vigentes.");
            case EVENT_CANCELLED -> new Message(
                    limit("Evento cancelado: " + eventName),
                    eventName + " fue cancelado. El reintegro de tus entradas se procesa automáticamente.");
            case EVENT_POSTPONED -> new Message(
                    limit("Evento postergado: " + eventName),
                    eventName + " fue postergado. Fecha anterior: " + text(payload, "previousStartsAt", "")
                            + ". Nueva fecha: " + text(payload, "startsAt", "")
                            + ". Tus entradas siguen vigentes.");
        };
    }

    static List<String> participants(JsonNode payload) {
        List<String> names = new ArrayList<>();
        payload.path("participants").forEach(node -> {
            if (node.isTextual() && !node.asText().isBlank()) {
                names.add(node.asText().trim());
            }
        });
        return names;
    }

    private static String text(JsonNode payload, String field, String fallback) {
        JsonNode node = payload.path(field);
        return node.isTextual() && !node.asText().isBlank() ? node.asText().trim() : fallback;
    }

    private static String limit(String value) {
        return value.length() <= MAX_SUBJECT ? value : value.substring(0, MAX_SUBJECT - 1) + "…";
    }
}
