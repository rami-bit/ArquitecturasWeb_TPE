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

    /** Idempotente: conserva el bloqueo incluso si el inventario aún no se materializó. */
    @Transactional
    public void cancel(UUID eventId) {
        store.lockEvent(eventId);
        store.cancelInventory(eventId);
    }
}
