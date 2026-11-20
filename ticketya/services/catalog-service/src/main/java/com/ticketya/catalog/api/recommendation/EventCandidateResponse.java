package com.ticketya.catalog.api.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Candidato publicado y futuro, sin información de disponibilidad ni cupos. */
@Schema(name = "EventCandidate")
public record EventCandidateResponse(
        UUID eventId,
        String name,
        String eventType,
        Instant startsAt,
        String city,
        List<String> participants,
        BigDecimal minPrice) {
}
