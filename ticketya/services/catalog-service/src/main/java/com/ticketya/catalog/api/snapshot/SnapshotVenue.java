package com.ticketya.catalog.api.snapshot;

import java.math.BigDecimal;

public record SnapshotVenue(
        String name,
        String address,
        String city,
        BigDecimal latitude,
        BigDecimal longitude) {
}
