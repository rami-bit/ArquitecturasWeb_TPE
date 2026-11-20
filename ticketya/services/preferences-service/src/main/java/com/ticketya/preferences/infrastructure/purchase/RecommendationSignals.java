package com.ticketya.preferences.infrastructure.purchase;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Señales de historial agregadas de un cliente, sin órdenes individuales. */
@Schema(name = "RecommendationSignals")
public record RecommendationSignals(
        UUID customerId,
        Set<UUID> purchasedEventIds,
        List<CoPurchase> coPurchases,
        Instant generatedAt) {

    @Schema(name = "CoPurchaseSignal")
    public record CoPurchase(UUID eventId, int count) {
    }

    /** Proyección compacta para el scorer. */
    public Map<UUID, Integer> countsByEvent() {
        return coPurchases.stream()
                .collect(java.util.stream.Collectors.toMap(
                        CoPurchase::eventId, CoPurchase::count, (left, right) -> left));
    }
}
