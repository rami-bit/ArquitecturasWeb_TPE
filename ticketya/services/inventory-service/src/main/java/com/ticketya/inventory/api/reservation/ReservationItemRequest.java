package com.ticketya.inventory.api.reservation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

import java.util.UUID;

@Schema(name = "ReservationItemRequest",
        description = "Exactly one of seatId or quantity must be supplied. "
                + "A seat item implies quantity 1.")
public record ReservationItemRequest(
        UUID sectorId,
        UUID seatId,
        @Min(1) Integer quantity) {
}
