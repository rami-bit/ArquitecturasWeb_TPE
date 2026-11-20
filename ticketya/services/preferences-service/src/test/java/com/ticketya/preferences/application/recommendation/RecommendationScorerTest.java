package com.ticketya.preferences.application.recommendation;

import com.ticketya.preferences.api.recommendation.RecommendedEvent;
import com.ticketya.preferences.domain.preferences.CustomerPreferences;
import com.ticketya.preferences.infrastructure.catalog.EventCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas puras del puntaje explicable; sin Spring ni Docker. */
class RecommendationScorerTest {

    private final RecommendationScorer scorer = new RecommendationScorer();
    private final Instant now = Instant.parse("2026-11-20T12:00:00Z");

    @Test
    void todasLasSenalesSumaDiecisieteYGeneraCincoMotivos() {
        UUID id = UUID.randomUUID();
        EventCandidate candidate = candidate(id, "CONCIERTO", now.plus(10, ChronoUnit.DAYS),
                "Buenos Aires", List.of("Artista A", "Artista B", "Artista C"));
        CustomerPreferences preferences = preferences("Buenos Aires", List.of("concierto"),
                List.of("artista a", "artista b", "artista c"));

        List<RecommendedEvent> ranked = scorer.rank(List.of(candidate), preferences, Set.of(),
                Map.of(id, 5), now);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).score()).isEqualTo(17);
        assertThat(ranked.get(0).reasons()).containsExactly(
                "FAVORITE_EVENT_TYPE", "FAVORITE_ARTIST", "SAME_CITY",
                "CO_PURCHASE", "UPCOMING_FALLBACK");
    }

    @Test
    void soloTipoFavoritoSumaTresYNoGeneraMotivoDeCiudad() {
        UUID id = UUID.randomUUID();
        EventCandidate candidate = candidate(id, "TEATRO", now.plus(60, ChronoUnit.DAYS),
                "Rosario", List.of("Otro"));
        CustomerPreferences preferences = preferences(null, List.of("teatro"), List.of());

        List<RecommendedEvent> ranked = scorer.rank(List.of(candidate), preferences, Set.of(),
                Map.of(), now);

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).score()).isEqualTo(3);
        assertThat(ranked.get(0).reasons()).containsExactly("FAVORITE_EVENT_TYPE");
    }

    @Test
    void artistasYCocomprasEstanTopeados() {
        UUID id = UUID.randomUUID();
        EventCandidate candidate = candidate(id, "MUSIC", now.plus(60, ChronoUnit.DAYS),
                "Rosario", List.of("A", "B", "C", "D"));
        CustomerPreferences preferences = preferences(null, List.of(), List.of("a", "b", "c", "d"));

        RecommendedEvent result = scorer.rank(List.of(candidate), preferences, Set.of(),
                Map.of(id, 99), now).get(0);

        // 2*3 por artistas + 5 por co-compra; sin tipo, ciudad ni proximidad.
        assertThat(result.score()).isEqualTo(11);
        assertThat(result.reasons()).containsExactly("FAVORITE_ARTIST", "CO_PURCHASE");
    }

    @Test
    void excluyeCompradosYeEventosSinPuntaje() {
        UUID comprado = UUID.randomUUID();
        UUID sinAfinidad = UUID.randomUUID();
        CustomerPreferences preferences = preferences(null, List.of("TEATRO"), List.of());
        UUID afine = UUID.randomUUID();
        List<RecommendedEvent> ranked = scorer.rank(List.of(
                        candidate(comprado, "TEATRO", now.plus(1, ChronoUnit.DAYS), "Rosario", List.of()),
                        candidate(sinAfinidad, "DEPORTES", now.plus(60, ChronoUnit.DAYS), "Rosario", List.of()),
                        candidate(afine, "TEATRO", now.plus(3, ChronoUnit.DAYS), "Rosario", List.of())),
                preferences, Set.of(comprado), Map.of(), now);

        assertThat(ranked).extracting(RecommendedEvent::eventId).containsExactly(afine);
    }

    @Test
    void ordenaPorPuntajeFechaYEventId() {
        UUID idA = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID idB = UUID.fromString("00000000-0000-4000-8000-000000000002");
        UUID idC = UUID.fromString("00000000-0000-4000-8000-000000000003");
        Instant masTemprano = now.plus(5, ChronoUnit.DAYS);
        Instant masTarde = now.plus(20, ChronoUnit.DAYS);
        CustomerPreferences preferences = preferences("Buenos Aires", List.of("TEATRO"), List.of());
        List<RecommendedEvent> ranked = scorer.rank(List.of(
                        candidate(idB, "TEATRO", masTarde, "Buenos Aires", List.of()),
                        candidate(idC, "TEATRO", masTemprano, "Buenos Aires", List.of()),
                        candidate(idA, "TEATRO", masTemprano, "Buenos Aires", List.of())),
                preferences, Set.of(), Map.of(), now);

        assertThat(ranked).extracting(RecommendedEvent::eventId).containsExactly(idA, idC, idB);
    }

    @Test
    void sinSenalesUsaFallbackConPuntajeCeroYOrdenPorFecha() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        Instant temprano = now.plus(2, ChronoUnit.DAYS);
        Instant tarde = now.plus(9, ChronoUnit.DAYS);
        List<RecommendedEvent> ranked = scorer.rank(List.of(
                        candidate(idB, "TEATRO", tarde, "Rosario", List.of()),
                        candidate(idA, "MUSIC", temprano, "Buenos Aires", List.of())),
                CustomerPreferences.empty("cliente"), Set.of(), Map.of(), now);

        assertThat(ranked).extracting(RecommendedEvent::score).containsExactly(0, 0);
        assertThat(ranked).extracting(RecommendedEvent::eventId).containsExactly(idA, idB);
        assertThat(ranked.get(0).reasons()).containsExactly("UPCOMING_FALLBACK");
    }

    private static EventCandidate candidate(UUID id, String type, Instant startsAt,
                                            String city, List<String> participants) {
        return new EventCandidate(id, "Evento " + id, type, startsAt, city, participants,
                new BigDecimal("5000.00"));
    }

    private static CustomerPreferences preferences(String city, List<String> types,
                                                   List<String> artists) {
        return new CustomerPreferences("cliente", city, types, artists,
                Instant.parse("2026-11-20T12:00:00Z"), 1L);
    }
}
