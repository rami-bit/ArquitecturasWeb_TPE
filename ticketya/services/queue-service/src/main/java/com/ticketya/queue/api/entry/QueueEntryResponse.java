package com.ticketya.queue.api.entry;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Estado de un turno de la fila virtual. {@code admissionToken} y
 * {@code admissionExpiresAt} sólo se informan cuando el estado es ADMITTED.
 */
@Schema(name = "QueueEntryResponse", requiredProperties = {
        "entryId", "eventId", "position", "peopleAhead", "batchSize", "batchNumber", "status"})
public record QueueEntryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID entryId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID eventId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long position,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long peopleAhead,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int batchSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long batchNumber,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        String admissionToken,
        OffsetDateTime admissionExpiresAt) {
}
