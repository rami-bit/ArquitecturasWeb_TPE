package com.ticketya.inventory.application;

import com.ticketya.inventory.infrastructure.persistence.InventoryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Expiration runs in its own transaction per reservation so that one failure or slow
 * reservation does not roll back the whole sweep.
 */
@Service
public class ReservationExpiryService {
    private final InventoryStore store;
    private final TransactionTemplate newTransaction;

    public ReservationExpiryService(InventoryStore store,
                                    PlatformTransactionManager transactionManager) {
        this.store = store;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void expireDueForEvent(UUID inventoryId) {
        for (UUID reservationId : store.dueReservationIdsForEvent(inventoryId)) {
            expire(reservationId);
        }
    }

    public void expireDue() {
        for (UUID reservationId : store.dueReservationIds()) {
            expire(reservationId);
        }
    }

    public void expire(UUID reservationId) {
        newTransaction.executeWithoutResult(status -> expireInTransaction(reservationId));
    }

    private void expireInTransaction(UUID reservationId) {
        if (store.expireReservation(reservationId) == 1) {
            store.releaseItems(reservationId);
            store.releaseGeneral(reservationId);
        }
    }
}
