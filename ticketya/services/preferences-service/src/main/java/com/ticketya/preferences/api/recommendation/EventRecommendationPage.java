package com.ticketya.preferences.api.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Página de recomendaciones con advertencias de degradación. */
@Schema(name = "EventRecommendationPage")
public record EventRecommendationPage(
        List<RecommendedEvent> items,
        List<String> warnings,
        Instant generatedAt) {
}
