package com.ticketya.catalog.domain.venue;

public record SeatPosition(String rowLabel, int seatNumber) {
    public SeatPosition {
        if (rowLabel == null || rowLabel.isBlank()) {
            throw new IllegalArgumentException("Seat row label is required");
        }
        if (seatNumber <= 0) {
            throw new IllegalArgumentException("Seat number must be positive");
        }
        rowLabel = rowLabel.trim();
    }
}
