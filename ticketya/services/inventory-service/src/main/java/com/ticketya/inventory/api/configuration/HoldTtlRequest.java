package com.ticketya.inventory.api.configuration;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "HoldTtlRequest")
public record HoldTtlRequest(
        @NotNull @Min(60) @Max(3600) Integer ttlSeconds) {
}
