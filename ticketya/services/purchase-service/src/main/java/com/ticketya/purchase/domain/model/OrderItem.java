package com.ticketya.purchase.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Ítem valorizado de una orden. */
public record OrderItem(
        UUID id,
        UUID orderId,
        UUID inventorySectorId,
        UUID sourceSectorId,
        String sectorName,
        String sectorType,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
}
