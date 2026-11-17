package com.ticketya.queue.api.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Solicitud interna de validación de una admisión de la fila virtual. */
public record AdmissionValidationRequest(
        @NotNull UUID eventId,
        @NotNull UUID clientId,
        @NotBlank String token,
        @Size(max = 120) String idempotencyKey,
        @Size(max = 200) String correlationId) {
}
