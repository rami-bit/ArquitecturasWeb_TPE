package com.ticketya.inventory.infrastructure.persistence;

import com.ticketya.inventory.domain.Reservation;
import com.ticketya.inventory.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    Optional<Reservation> findByOrderId(UUID orderId);

    List<Reservation> findByStatusAndExpiresAtLessThanEqual(
            ReservationStatus status, OffsetDateTime now);

    List<Reservation> findByEventInventoryIdAndStatusAndExpiresAtLessThanEqual(
            UUID eventInventoryId, ReservationStatus status, OffsetDateTime now);
}
