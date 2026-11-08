package com.ticketya.inventory.api.configuration;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "EventHoldConfiguration")
public record EventHoldConfiguration(
        UUID eventId,
        int holdTtlSeconds) {
}
