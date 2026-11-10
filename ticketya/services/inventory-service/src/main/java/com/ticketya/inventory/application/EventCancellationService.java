package com.ticketya.inventory.application;

import com.ticketya.inventory.infrastructure.persistence.InventoryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EventCancellationService {
    private final InventoryStore store;

    public EventCancellationService(InventoryStore store) {
        this.store = store;
    }

    /** Idempotente: si el inventario no existe todavía, no hay nada que bloquear. */
    @Transactional
    public void cancel(UUID eventId) {
        store.cancelInventory(eventId);
    }
}
