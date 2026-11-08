package com.ticketya.inventory.infrastructure.persistence;

import com.ticketya.inventory.domain.ReservationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationItemRepository extends JpaRepository<ReservationItem, UUID> {
    List<ReservationItem> findByReservationIdOrderBySectorIdAsc(UUID reservationId);
}
