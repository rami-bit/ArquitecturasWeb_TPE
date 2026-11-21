package com.ticketya.notification.api.ingest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Hecho de orden publicado por Purchase ({@code ORDER_CONFIRMED} u
 * {@code ORDER_CANCELLED}). Los campos del evento son opcionales: sólo
 * enriquecen el texto del mensaje.
 */
@Schema(name = "OrderNotificationRequest")
public record OrderNotificationRequest(
        @NotBlank String type,
        @NotNull UUID orderId,
        @NotNull UUID customerId,
        @Size(max = 120) String correlationId,
        UUID eventId,
        @Size(max = 200) String eventName,
        OffsetDateTime eventStartsAt,
        BigDecimal totalAmount) {
}
