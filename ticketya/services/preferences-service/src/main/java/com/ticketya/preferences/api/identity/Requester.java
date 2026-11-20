package com.ticketya.preferences.api.identity;

import java.util.UUID;

/**
 * Identidad confiable derivada por el API Gateway: el servicio no valida JWT,
 * sólo acepta {@code X-User-Id} (UUID) y {@code X-User-Role} (CLIENT).
 */
public record Requester(UUID id, String role) {

    public boolean isClient() {
        return "CLIENT".equals(role);
    }
}
