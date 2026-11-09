package com.ticketya.ticket.infrastructure.persistence;

import com.ticketya.ticket.domain.TicketIssuance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TicketIssuanceRepository extends JpaRepository<TicketIssuance, UUID> {
    Optional<TicketIssuance> findByOrderIdAndOrderItemId(UUID orderId, UUID orderItemId);

    @Modifying
    @Query(value = """
            INSERT INTO ticket_issuances
                (id, order_id, order_item_id, request_fingerprint, created_at)
            VALUES
                (:id, :orderId, :orderItemId, :fingerprint, :createdAt)
            ON CONFLICT (order_id, order_item_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("orderId") UUID orderId,
            @Param("orderItemId") UUID orderItemId,
            @Param("fingerprint") String fingerprint,
            @Param("createdAt") OffsetDateTime createdAt);
}
