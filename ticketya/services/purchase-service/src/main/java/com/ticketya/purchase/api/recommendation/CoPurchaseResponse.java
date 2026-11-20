package com.ticketya.purchase.api.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** Cantidad de clientes distintos que compraron un evento junto a uno del consultado. */
@Schema(name = "CoPurchaseSignal")
public record CoPurchaseResponse(UUID eventId, int count) {
}
