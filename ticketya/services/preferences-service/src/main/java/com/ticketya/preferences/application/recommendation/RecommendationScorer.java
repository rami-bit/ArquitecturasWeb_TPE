package com.ticketya.preferences.application.recommendation;

import com.ticketya.preferences.api.recommendation.RecommendedEvent;
import com.ticketya.preferences.domain.preferences.CustomerPreferences;
import com.ticketya.preferences.infrastructure.catalog.EventCandidate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puntaje lineal y determinista (sin machine learning):
 *
 * <pre>
 * score = 3 * (tipo favorito)
 *       + 2 * min(3, artistas del evento en favoritos)
 *       + 2 * (ciudad del evento == ciudad del cliente)
 *       + min(5, co-compras)
 *       + 1 * (startsAt dentro de los próximos 30 días)
 * </pre>
 *
 * <p>Máximo teórico 17. Cada componente que suma informa su motivo. En modo
 * normal un candidato con puntaje cero se descarta. Sin preferencias ni
 * historial se devuelven los próximos eventos con puntaje cero y el motivo
 * {@code UPCOMING_FALLBACK}.
 */
@Component
public class RecommendationScorer {

    public static final String FAVORITE_EVENT_TYPE = "FAVORITE_EVENT_TYPE";
    public static final String FAVORITE_ARTIST = "FAVORITE_ARTIST";
    public static final String SAME_CITY = "SAME_CITY";
    public static final String CO_PURCHASE = "CO_PURCHASE";
    public static final String UPCOMING_FALLBACK = "UPCOMING_FALLBACK";

    private static final Comparator<RecommendedEvent> ORDER =
            Comparator.comparingInt(RecommendedEvent::score).reversed()
                    .thenComparing(RecommendedEvent::startsAt,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(RecommendedEvent::eventId);

    /**
     * Puntúa, filtra y ordena los candidatos. El límite se aplica después en
     * {@link RecommendationService}, para no descartar los más afines.
     */
    public List<RecommendedEvent> rank(List<EventCandidate> candidates,
                                       CustomerPreferences preferences,
                                       Set<UUID> purchasedEventIds,
                                       Map<UUID, Integer> coPurchases,
                                       Instant now) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<UUID> purchased = purchasedEventIds == null ? Set.of() : purchasedEventIds;
        Map<UUID, Integer> co = coPurchases == null ? Map.of() : coPurchases;
        if (isFallback(preferences, purchased, co)) {
            return candidates.stream()
                    .filter(candidate -> !purchased.contains(candidate.eventId()))
                    .map(candidate -> new RecommendedEvent(
                            candidate.eventId(), candidate.name(), candidate.eventType(),
                            candidate.startsAt(), candidate.city(), candidate.participants(),
                            candidate.minPrice(), 0, List.of(UPCOMING_FALLBACK)))
                    .sorted(Comparator.comparing(RecommendedEvent::startsAt,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(RecommendedEvent::eventId))
                    .toList();
        }

        Set<String> favoriteTypes = preferences == null
                ? Set.of() : new LinkedHashSet<>(preferences.normalizedEventTypes());
        Set<String> favoriteArtists = preferences == null
                ? Set.of() : new LinkedHashSet<>(preferences.normalizedFavoriteArtists());
        String favoriteCity = preferences == null || preferences.getCity() == null
                ? "" : CustomerPreferences.normalize(preferences.getCity());
        Instant horizon = now.plus(30, ChronoUnit.DAYS);

        List<RecommendedEvent> ranked = new ArrayList<>();
        for (EventCandidate candidate : candidates) {
            if (candidate == null || candidate.eventId() == null
                    || purchased.contains(candidate.eventId())) {
                continue;
            }
            int score = 0;
            List<String> reasons = new ArrayList<>();

            if (favoriteTypes.contains(CustomerPreferences.normalize(candidate.eventType()))) {
                score += 3;
                reasons.add(FAVORITE_EVENT_TYPE);
            }
            long artistMatches = candidate.participants() == null ? 0
                    : candidate.participants().stream()
                            .map(CustomerPreferences::normalize)
                            .filter(favoriteArtists::contains)
                            .distinct()
                            .count();
            if (artistMatches > 0) {
                score += 2 * (int) Math.min(3, artistMatches);
                reasons.add(FAVORITE_ARTIST);
            }
            if (!favoriteCity.isEmpty()
                    && favoriteCity.equals(CustomerPreferences.normalize(candidate.city()))) {
                score += 2;
                reasons.add(SAME_CITY);
            }
            int coCount = co.getOrDefault(candidate.eventId(), 0);
            if (coCount > 0) {
                score += Math.min(5, coCount);
                reasons.add(CO_PURCHASE);
            }
            if (candidate.startsAt() != null
                    && !candidate.startsAt().isBefore(now)
                    && !candidate.startsAt().isAfter(horizon)) {
                score += 1;
                reasons.add(UPCOMING_FALLBACK);
            }
            if (score == 0) {
                continue;
            }
            ranked.add(new RecommendedEvent(
                    candidate.eventId(), candidate.name(), candidate.eventType(),
                    candidate.startsAt(), candidate.city(), candidate.participants(),
                    candidate.minPrice(), score, List.copyOf(reasons)));
        }
        ranked.sort(ORDER);
        return ranked;
    }

    private static boolean isFallback(CustomerPreferences preferences,
                                      Set<UUID> purchased, Map<UUID, Integer> co) {
        boolean hasPreferences = preferences != null
                && ((preferences.getCity() != null && !preferences.getCity().isBlank())
                    || !preferences.getEventTypes().isEmpty()
                    || !preferences.getFavoriteArtists().isEmpty());
        boolean hasCoPurchase = co.values().stream().anyMatch(count -> count > 0);
        return !hasPreferences && purchased.isEmpty() && !hasCoPurchase;
    }
}
