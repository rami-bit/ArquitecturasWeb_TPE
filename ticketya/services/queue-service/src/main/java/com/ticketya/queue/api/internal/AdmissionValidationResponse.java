package com.ticketya.queue.api.internal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Respuesta de una admisión aceptada: turno consumido y ventana vigente. */
@Schema(name = "AdmissionValidationResponse", requiredProperties = {"admitted", "admissionId", "expiresAt"})
public record AdmissionValidationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean admitted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID admissionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime expiresAt) {
}
