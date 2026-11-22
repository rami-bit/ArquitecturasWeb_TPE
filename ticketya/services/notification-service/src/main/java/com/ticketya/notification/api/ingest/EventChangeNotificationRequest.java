package com.ticketya.notification.api.ingest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cambio de ciclo de vida de un evento publicado por Catalog. {@code changeId}
 * identifica cada transición; reenviarla con el mismo valor no duplica avisos.
 */
@Schema(name = "EventChangeNotificationRequest")
public record EventChangeNotificationRequest(
        @NotBlank String type,
        @NotNull UUID eventId,
        UUID changeId,
        @NotBlank @Size(max = 200) String eventName,
        OffsetDateTime startsAt,
        OffsetDateTime previousStartsAt,
        @Size(max = 120) String city,
        @Size(max = 50) List<@Size(max = 200) String> participants,
        @Size(max = 120) String correlationId) {
}
