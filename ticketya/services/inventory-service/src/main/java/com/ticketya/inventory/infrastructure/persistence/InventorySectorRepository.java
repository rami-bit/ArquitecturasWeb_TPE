package com.ticketya.inventory.infrastructure.persistence;

import com.ticketya.inventory.domain.InventorySector;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventorySectorRepository extends JpaRepository<InventorySector, UUID> {
    List<InventorySector> findByEventInventoryIdOrderByNameAscIdAsc(UUID eventInventoryId);

    Optional<InventorySector> findByIdAndEventInventoryId(UUID id, UUID eventInventoryId);
}
