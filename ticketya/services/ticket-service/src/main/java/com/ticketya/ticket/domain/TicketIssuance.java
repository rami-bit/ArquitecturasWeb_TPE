package com.ticketya.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ticket_issuances")
public class TicketIssuance {
    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TicketIssuance() {
    }

    public TicketIssuance(UUID id, UUID orderId, UUID orderItemId,
                          String requestFingerprint, OffsetDateTime createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.requestFingerprint = requestFingerprint;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getOrderItemId() {
        return orderItemId;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
