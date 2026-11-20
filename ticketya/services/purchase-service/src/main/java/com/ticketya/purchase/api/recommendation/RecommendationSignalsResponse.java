package com.ticketya.purchase.api.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Señales agregadas de historial de compras de un cliente, sin órdenes individuales. */
@Schema(name = "RecommendationSignals")
public record RecommendationSignalsResponse(
        UUID customerId,
        List<UUID> purchasedEventIds,
        List<CoPurchaseResponse> coPurchases,
        Instant generatedAt) {
}
