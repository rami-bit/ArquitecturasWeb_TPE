package com.ticketya.queue.api.identity;

import java.util.UUID;

/**
 * Identidad confiable derivada por el API Gateway: el servicio no valida JWT,
 * sólo acepta {@code X-User-Id} (UUID) y {@code X-User-Role} (CLIENT o ADMIN).
 */
public record Requester(UUID id, String role) {
}
