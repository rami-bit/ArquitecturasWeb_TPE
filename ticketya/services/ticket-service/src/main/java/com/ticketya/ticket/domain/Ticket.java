package com.ticketya.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id
    private UUID id;

    @Column(name = "issuance_id", nullable = false)
    private UUID issuanceId;

    @Column(name = "unit_number", nullable = false)
    private int unitNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_name", nullable = false, length = 200)
    private String eventName;

    @Column(name = "event_starts_at", nullable = false)
    private OffsetDateTime eventStartsAt;

    @Column(name = "venue_name", nullable = false, length = 200)
    private String venueName;

    @Column(name = "venue_city", nullable = false, length = 120)
    private String venueCity;

    @Column(name = "sector_name", nullable = false, length = 120)
    private String sectorName;

    @Column(name = "row_label", length = 40)
    private String rowLabel;

    @Column(name = "seat_number")
    private Integer seatNumber;

    @Column(name = "qr_hash", nullable = false, length = 64)
    private String qrHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TicketStatus status;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    protected Ticket() {
    }

    public Ticket(UUID id, UUID issuanceId, int unitNumber, UUID customerId,
                  UUID eventId, String eventName, OffsetDateTime eventStartsAt,
                  String venueName, String venueCity, String sectorName,
                  String rowLabel, Integer seatNumber, String qrHash,
                  OffsetDateTime issuedAt) {
        this.id = id;
        this.issuanceId = issuanceId;
        this.unitNumber = unitNumber;
        this.customerId = customerId;
        this.eventId = eventId;
        this.eventName = eventName;
        this.eventStartsAt = eventStartsAt;
        this.venueName = venueName;
        this.venueCity = venueCity;
        this.sectorName = sectorName;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.qrHash = qrHash;
        this.status = TicketStatus.ISSUED;
        this.issuedAt = issuedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getIssuanceId() {
        return issuanceId;
    }

    public int getUnitNumber() {
        return unitNumber;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public OffsetDateTime getEventStartsAt() {
        return eventStartsAt;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getVenueCity() {
        return venueCity;
    }

    public String getSectorName() {
        return sectorName;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public Integer getSeatNumber() {
        return seatNumber;
    }

    public String getQrHash() {
        return qrHash;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void revoke() {
        if (status == TicketStatus.USED) {
            throw new IllegalStateException("A used ticket cannot be revoked");
        }
        status = TicketStatus.REVOKED;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }
}
