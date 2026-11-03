package com.ticketya.catalog.domain.venue;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "venue_sectors")
public class VenueSector {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "sector_type", nullable = false, length = 32)
    private SectorType type;

    @Column(name = "general_capacity")
    private Integer generalCapacity;

    @OneToMany(mappedBy = "sector", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rowLabel ASC, seatNumber ASC")
    private final List<VenueSeat> seats = new ArrayList<>();

    protected VenueSector() {
    }

    static VenueSector numbered(Venue venue, String name, Collection<SeatPosition> seatPositions) {
        if (seatPositions == null || seatPositions.isEmpty()) {
            throw new IllegalArgumentException("A numbered sector requires at least one seat");
        }

        List<SeatPosition> positions = List.copyOf(seatPositions);
        Set<SeatPosition> uniquePositions = new HashSet<>(positions);
        if (uniquePositions.size() != positions.size()) {
            throw new IllegalArgumentException("Seat row and number must be unique within the sector");
        }

        VenueSector sector = new VenueSector(venue, name, SectorType.NUMBERED, null);
        positions.forEach(position -> sector.seats.add(new VenueSeat(sector, position)));
        return sector;
    }

    static VenueSector generalAdmission(Venue venue, String name, int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("General admission capacity must be positive");
        }
        return new VenueSector(venue, name, SectorType.GENERAL_ADMISSION, capacity);
    }

    private VenueSector(Venue venue, String name, SectorType type, Integer generalCapacity) {
        this.venue = Objects.requireNonNull(venue);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Sector name is required");
        }
        this.name = name.trim();
        this.type = Objects.requireNonNull(type);
        this.generalCapacity = generalCapacity;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public SectorType getType() {
        return type;
    }

    public int getCapacity() {
        return type == SectorType.NUMBERED ? seats.size() : generalCapacity;
    }

    public List<VenueSeat> getSeats() {
        return Collections.unmodifiableList(seats);
    }
}
