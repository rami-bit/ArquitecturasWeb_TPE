package com.ticketya.inventory.infrastructure.persistence;

import com.ticketya.inventory.domain.EventInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventInventoryRepository extends JpaRepository<EventInventory, UUID> {
    Optional<EventInventory> findByEventId(UUID eventId);
}
