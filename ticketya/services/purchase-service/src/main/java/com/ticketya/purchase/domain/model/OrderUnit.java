package com.ticketya.purchase.domain.model;

import java.util.UUID;

/** Unidad concreta de un ítem (asiento numerado o cupo general). */
public record OrderUnit(
        UUID id,
        UUID orderItemId,
        int unitNumber,
        UUID inventorySeatId,
        UUID sourceSeatId,
        String rowLabel,
        Integer seatNumber) {
}
