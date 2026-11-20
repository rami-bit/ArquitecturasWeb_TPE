package com.ticketya.preferences.application.recommendation;

import com.ticketya.preferences.api.error.ApiProblemException;
import com.ticketya.preferences.api.recommendation.EventRecommendationPage;
import com.ticketya.preferences.domain.preferences.CustomerPreferences;
import com.ticketya.preferences.domain.preferences.CustomerPreferencesRepository;
import com.ticketya.preferences.infrastructure.catalog.CatalogCandidateClient;
import com.ticketya.preferences.infrastructure.purchase.PurchaseSignalsClient;
import com.ticketya.preferences.infrastructure.purchase.RecommendationSignals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orquesta preferencias, señales de compra y candidatos del catálogo. Lee un
 * documento por clave, un agregado acotado en Purchase y hasta 200 candidatos
 * en Catalog en cada consulta: no hay cache ni reintentos.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    public static final String HISTORY_UNAVAILABLE = "HISTORY_UNAVAILABLE";

    private final CustomerPreferencesRepository repository;
    private final PurchaseSignalsClient purchaseClient;
    private final CatalogCandidateClient catalogClient;
    private final RecommendationScorer scorer;

    public RecommendationService(CustomerPreferencesRepository repository,
                                 PurchaseSignalsClient purchaseClient,
                                 CatalogCandidateClient catalogClient,
                                 RecommendationScorer scorer) {
        this.repository = repository;
        this.purchaseClient = purchaseClient;
        this.catalogClient = catalogClient;
        this.scorer = scorer;
    }

    public EventRecommendationPage recommend(UUID customerId, Integer limit) {
        int effectiveLimit = validateLimit(limit);
        Instant now = Instant.now();

        CustomerPreferences preferences = repository.findById(customerId.toString())
                .orElseGet(() -> CustomerPreferences.empty(customerId.toString()));
        List<String> warnings = new ArrayList<>();

        Set<UUID> purchased = Set.of();
        Map<UUID, Integer> coPurchases = Map.of();
        try {
            RecommendationSignals signals = purchaseClient.fetch(customerId);
            if (signals != null) {
                purchased = signals.purchasedEventIds() == null ? Set.of() : signals.purchasedEventIds();
                coPurchases = signals.countsByEvent();
            }
        } catch (ApiProblemException unavailable) {
            log.warn("Degrading recommendations for customer {}: history unavailable", customerId);
            warnings.add(HISTORY_UNAVAILABLE);
        }

        List<com.ticketya.preferences.infrastructure.catalog.EventCandidate> candidates =
                catalogClient.candidates();

        List<com.ticketya.preferences.api.recommendation.RecommendedEvent> ranked =
                scorer.rank(candidates, preferences, purchased, coPurchases, now);
        List<com.ticketya.preferences.api.recommendation.RecommendedEvent> items =
                ranked.size() > effectiveLimit ? List.copyOf(ranked.subList(0, effectiveLimit)) : ranked;

        return new EventRecommendationPage(items, List.copyOf(warnings), now);
    }

    private static int validateLimit(Integer limit) {
        if (limit == null || limit < 1 || limit > 20) {
            throw ApiProblemException.invalidLimit("limit debe ser un número entre 1 y 20");
        }
        return limit;
    }
}
