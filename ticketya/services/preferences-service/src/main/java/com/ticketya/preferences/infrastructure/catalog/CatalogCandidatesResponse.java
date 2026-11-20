package com.ticketya.preferences.infrastructure.catalog;

import java.time.Instant;
import java.util.List;

/** Respuesta del contrato interno de candidatos del catálogo. */
public record CatalogCandidatesResponse(List<EventCandidate> items, Instant generatedAt) {
}
