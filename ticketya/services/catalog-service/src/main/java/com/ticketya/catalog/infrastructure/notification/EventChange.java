package com.ticketya.catalog.infrastructure.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cambio de ciclo de vida de un evento a notificar (PDF-F11). Se arma dentro
 * de la transacción (con lugar y participantes cargados) y se envía después
 * del commit. {@code changeId} identifica la transición para deduplicarla.
 */
public record EventChange(String type, UUID eventId, UUID changeId, String eventName, OffsetDateTime startsAt,
                          String city, List<String> participants) {

    public static EventChange of(String type, UUID eventId, String eventName, OffsetDateTime startsAt, String city,
                                 List<String> participants) {
        return new EventChange(type, eventId, UUID.randomUUID(), eventName, startsAt, city,
                participants == null ? List.of() : List.copyOf(participants));
    }
}
