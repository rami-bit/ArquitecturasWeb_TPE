package com.ticketya.ticket.api.access;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "AccessValidationRequest")
public record AccessValidationRequest(
        @NotBlank @Size(max = 512) String qrPayload,
        @Size(max = 80) String gate) {
}
