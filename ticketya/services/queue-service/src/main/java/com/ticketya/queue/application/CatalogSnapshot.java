package com.ticketya.queue.application;

import java.util.UUID;

/**
 * Contrato interno de Catalog para la fila virtual: sólo el identificador del
 * evento y la marca de alta demanda. Si el JSON no trae {@code highDemand},
 * se trata como {@code false} (el campo aún puede estar siendo agregado).
 */
public record CatalogSnapshot(UUID eventId, Boolean highDemand) {

    public boolean isHighDemand() {
        return Boolean.TRUE.equals(highDemand);
    }
}
