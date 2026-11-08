package com.ticketya.inventory.application;

import com.ticketya.inventory.api.configuration.EventHoldConfiguration;
import com.ticketya.inventory.api.error.ApiProblemException;
import com.ticketya.inventory.infrastructure.persistence.InventoryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HoldTtlService {
    private final InventoryStore store;

    public HoldTtlService(InventoryStore store) {
        this.store = store;
    }

    @Transactional
    public EventHoldConfiguration update(UUID eventId, Integer ttlSeconds) {
        if (ttlSeconds == null || ttlSeconds < 60 || ttlSeconds > 3600) {
            throw ApiProblemException.badRequest("ttlSeconds must be between 60 and 3600");
        }
        boolean exists = store.findInventory(eventId).isPresent();
        if (!exists) {
            throw ApiProblemException.notFound(
                    "The event does not have a materialized inventory");
        }
        int updated = store.updateHoldTtl(eventId, ttlSeconds);
        if (updated == 0) {
            throw ApiProblemException.notFound(
                    "The event does not have a materialized inventory");
        }
        return new EventHoldConfiguration(eventId, ttlSeconds);
    }
}
