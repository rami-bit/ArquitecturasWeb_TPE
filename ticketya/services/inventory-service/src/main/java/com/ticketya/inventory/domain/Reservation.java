package com.ticketya.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    private UUID id;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "event_inventory_id", nullable = false)
    private UUID eventInventoryId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;
    @Column(name = "request_fingerprint", nullable = false)
    private String requestFingerprint;
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;
    @Column(name = "released_at")
    private OffsetDateTime releasedAt;
    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    protected Reservation() {
    }

    public Reservation(UUID id, UUID orderId, UUID eventInventoryId, ReservationStatus status,
                       String requestFingerprint, OffsetDateTime expiresAt,
                       OffsetDateTime createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.eventInventoryId = eventInventoryId;
        this.status = status;
        this.requestFingerprint = requestFingerprint;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getEventInventoryId() {
        return eventInventoryId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public OffsetDateTime getReleasedAt() {
        return releasedAt;
    }

    public OffsetDateTime getExpiredAt() {
        return expiredAt;
    }
}
