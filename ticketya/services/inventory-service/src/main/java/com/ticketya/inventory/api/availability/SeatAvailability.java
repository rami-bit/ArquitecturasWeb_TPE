package com.ticketya.inventory.api.availability;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "SeatAvailability")
public record SeatAvailability(
        UUID seatId,
        String row,
        int number,
        String status) {
}
