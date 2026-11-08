package com.ticketya.catalog.api.snapshot;

import java.util.UUID;

public record SnapshotSeat(UUID sourceSeatId, String row, int number) {
}
