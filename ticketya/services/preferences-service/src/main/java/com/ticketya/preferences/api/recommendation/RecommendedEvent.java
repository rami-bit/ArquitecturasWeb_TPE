package com.ticketya.preferences.api.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Evento recomendado con su puntaje explicable y los motivos que lo produjeron. */
@Schema(name = "RecommendedEvent")
public record RecommendedEvent(
        UUID eventId,
        String name,
        String eventType,
        Instant startsAt,
        String city,
        List<String> participants,
        BigDecimal minPrice,
        int score,
        List<String> reasons) {
}
