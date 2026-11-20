package com.ticketya.catalog.api.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Listado acotado de candidatos de recomendación. */
@Schema(name = "EventCandidatePage")
public record EventCandidatePageResponse(
        List<EventCandidateResponse> items,
        Instant generatedAt) {
}
