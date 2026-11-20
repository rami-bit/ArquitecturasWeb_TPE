package com.ticketya.preferences.application.recommendation;

import com.ticketya.preferences.api.error.ApiProblemException;
import com.ticketya.preferences.api.recommendation.EventRecommendationPage;
import com.ticketya.preferences.domain.preferences.CustomerPreferences;
import com.ticketya.preferences.domain.preferences.CustomerPreferencesRepository;
import com.ticketya.preferences.infrastructure.catalog.CatalogCandidateClient;
import com.ticketya.preferences.infrastructure.catalog.EventCandidate;
import com.ticketya.preferences.infrastructure.purchase.PurchaseSignalsClient;
import com.ticketya.preferences.infrastructure.purchase.RecommendationSignals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Orquestación de recomendaciones con stubs: límites, degradación y fallback. */
class RecommendationServiceTest {

    private static final UUID CUSTOMER = UUID.fromString("8f14e45f-ceea-467f-a8a4-0f1b2f0f9c11");

    private CustomerPreferencesRepository repository;
    private PurchaseSignalsClient purchase;
    private CatalogCandidateClient catalog;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        repository = mock(CustomerPreferencesRepository.class);
        purchase = mock(PurchaseSignalsClient.class);
        catalog = mock(CatalogCandidateClient.class);
        service = new RecommendationService(repository, purchase, catalog, new RecommendationScorer());
        when(purchase.fetch(CUSTOMER)).thenReturn(new RecommendationSignals(
                CUSTOMER, Set.of(), List.of(), Instant.now()));
        when(catalog.candidates()).thenReturn(List.of(candidate(UUID.randomUUID(), "TEATRO", "Buenos Aires")));
    }

    @Test
    void limiteFueraDeRangoResponde400() {
        assertThatThrownBy(() -> service.recommend(CUSTOMER, 0))
                .isInstanceOf(ApiProblemException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> service.recommend(CUSTOMER, 21))
                .isInstanceOf(ApiProblemException.class);
    }

    @Test
    void compraNoDisponibleDegradaConAdvertenciaYSigueRecomendando() {
        when(repository.findById(CUSTOMER.toString())).thenReturn(Optional.of(preferences()));
        when(purchase.fetch(CUSTOMER))
                .thenThrow(ApiProblemException.serviceUnavailable("Purchase is unavailable"));

        EventRecommendationPage page = service.recommend(CUSTOMER, 10);

        assertThat(page.warnings()).containsExactly("HISTORY_UNAVAILABLE");
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).reasons()).doesNotContain("CO_PURCHASE");
    }

    @Test
    void catalogoNoDisponibleResponde503() {
        when(catalog.candidates())
                .thenThrow(ApiProblemException.serviceUnavailable("Catalog is unavailable"));

        assertThatThrownBy(() -> service.recommend(CUSTOMER, 10))
                .isInstanceOf(ApiProblemException.class)
                .hasMessageContaining("Catalog");
    }

    @Test
    void sinPreferenciasNiHistorialDevuelveFallbackConPuntajeCero() {
        when(repository.findById(CUSTOMER.toString())).thenReturn(Optional.empty());

        EventRecommendationPage page = service.recommend(CUSTOMER, 10);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).score()).isZero();
        assertThat(page.items().get(0).reasons()).containsExactly("UPCOMING_FALLBACK");
    }

    @Test
    void elLimiteSeAplicaDespuesDeOrdenar() {
        when(repository.findById(CUSTOMER.toString())).thenReturn(Optional.of(preferences()));
        UUID idA = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID idB = UUID.fromString("00000000-0000-4000-8000-000000000002");
        when(catalog.candidates()).thenReturn(List.of(
                candidate(idB, "MUSIC", "Rosario"),
                candidate(idA, "TEATRO", "Buenos Aires")));

        EventRecommendationPage page = service.recommend(CUSTOMER, 1);

        assertThat(page.items()).extracting(item -> item.eventId()).containsExactly(idA);
        verify(catalog).candidates();
    }

    private static CustomerPreferences preferences() {
        return new CustomerPreferences(CUSTOMER.toString(), "Buenos Aires",
                List.of("TEATRO"), List.of(), Instant.now(), 1L);
    }

    private static EventCandidate candidate(UUID id, String type, String city) {
        return new EventCandidate(id, "Evento " + id, type, Instant.now().plusSeconds(86_400),
                city, List.of("Artista"), new BigDecimal("1000.00"));
    }
}
