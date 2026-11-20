package com.ticketya.preferences.infrastructure.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Candidato publicado y futuro, sin información de disponibilidad. */
@Schema(name = "EventCandidate")
public record EventCandidate(
        UUID eventId,
        String name,
        String eventType,
        Instant startsAt,
        String city,
        List<String> participants,
        BigDecimal minPrice) {
}
