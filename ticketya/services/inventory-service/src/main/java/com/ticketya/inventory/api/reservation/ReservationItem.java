package com.ticketya.inventory.api.reservation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "ReservationItem")
public record ReservationItem(
        UUID sectorId,
        UUID seatId,
        int quantity) {
}
