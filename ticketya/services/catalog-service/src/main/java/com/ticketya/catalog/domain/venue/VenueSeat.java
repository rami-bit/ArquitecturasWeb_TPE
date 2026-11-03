package com.ticketya.catalog.domain.venue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "venue_seats")
public class VenueSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_sector_id", nullable = false)
    private VenueSector sector;

    @Column(name = "row_label", nullable = false, length = 40)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    protected VenueSeat() {
    }

    VenueSeat(VenueSector sector, SeatPosition position) {
        this.sector = sector;
        this.rowLabel = position.rowLabel();
        this.seatNumber = position.seatNumber();
    }

    public UUID getId() {
        return id;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getSeatNumber() {
        return seatNumber;
    }
}
