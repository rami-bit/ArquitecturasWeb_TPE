package com.ticketya.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketya.notification.infrastructure.JdbcNotificationRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Registro idempotente de los hechos recibidos. La clave de deduplicación es
 * {@code tipo:orderId} para órdenes, {@code tipo:eventId} para publicación y
 * cancelación (ocurren una vez) y {@code tipo:eventId:changeId} para
 * suspensión y reanudación, que pueden repetirse en la vida de un evento.
 */
@Service
public class IngestService {
    private final JdbcNotificationRepository repository;

    public IngestService(JdbcNotificationRepository repository) {
        this.repository = repository;
    }

    public record IngestResult(UUID triggerId, boolean duplicate) {
    }

    public IngestResult ingest(TriggerType type, String resourceKey, UUID changeId, JsonNode payload,
                               String correlationId) {
        String dedupeKey = type.name() + ":" + resourceKey
                + (type.repeatable() ? ":" + (changeId == null ? UUID.randomUUID() : changeId) : "");
        UUID id = UUID.randomUUID();
        boolean inserted = repository.insertTrigger(id, dedupeKey, type, payload, correlationId);
        return inserted ? new IngestResult(id, false)
                : new IngestResult(repository.triggerIdByDedupeKey(dedupeKey), true);
    }
}
