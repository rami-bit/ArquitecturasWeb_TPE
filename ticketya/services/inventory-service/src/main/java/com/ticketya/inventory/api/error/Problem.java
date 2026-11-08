package com.ticketya.inventory.api.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;

@Schema(name = "Problem", requiredProperties = {
        "type", "title", "status", "detail", "instance", "correlationId"})
public record Problem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) URI type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String detail,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) URI instance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String correlationId) {
}
