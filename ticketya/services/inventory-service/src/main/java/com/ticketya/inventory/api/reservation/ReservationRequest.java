package com.ticketya.inventory.api.reservation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(name = "ReservationRequest")
public record ReservationRequest(
        @NotNull UUID orderId,
        @NotNull UUID eventId,
        @Min(60) @Max(3600) Integer ttlSeconds,
        @NotEmpty @Valid List<ReservationItemRequest> items) {
}
