package com.ticketya.inventory.infrastructure.persistence;

import com.ticketya.inventory.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    List<InventoryItem> findBySectorIdOrderByRowLabelAscSeatNumberAsc(UUID sectorId);

    Optional<InventoryItem> findByIdAndSectorId(UUID id, UUID sectorId);
}
