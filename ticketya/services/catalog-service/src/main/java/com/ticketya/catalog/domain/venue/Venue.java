package com.ticketya.catalog.domain.venue;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "venues")
public class Venue {
    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private final List<VenueSector> sectors = new ArrayList<>();

    protected Venue() {
    }

    public Venue(
            String name,
            String address,
            String city,
            BigDecimal latitude,
            BigDecimal longitude) {
        this.name = required(name, "Venue name");
        this.address = required(address, "Venue address");
        this.city = required(city, "Venue city");
        this.latitude = coordinate(latitude, MIN_LATITUDE, MAX_LATITUDE, "Latitude");
        this.longitude = coordinate(longitude, MIN_LONGITUDE, MAX_LONGITUDE, "Longitude");
    }

    public VenueSector addNumberedSector(String name, Collection<SeatPosition> seatPositions) {
        VenueSector sector = VenueSector.numbered(this, name, seatPositions);
        sectors.add(sector);
        return sector;
    }

    public void updateDetails(String name, String address, String city, BigDecimal latitude, BigDecimal longitude) {
        String validatedName = required(name, "Venue name");
        String validatedAddress = required(address, "Venue address");
        String validatedCity = required(city, "Venue city");
        BigDecimal validatedLatitude = coordinate(latitude, MIN_LATITUDE, MAX_LATITUDE, "Latitude");
        BigDecimal validatedLongitude = coordinate(longitude, MIN_LONGITUDE, MAX_LONGITUDE, "Longitude");
        this.name = validatedName;
        this.address = validatedAddress;
        this.city = validatedCity;
        this.latitude = validatedLatitude;
        this.longitude = validatedLongitude;
    }

    public VenueSector addGeneralAdmissionSector(String name, int capacity) {
        VenueSector sector = VenueSector.generalAdmission(this, name, capacity);
        sectors.add(sector);
        return sector;
    }

    public void replaceSectors(Collection<SectorDefinition> definitions) {
        if (definitions == null) throw new IllegalArgumentException("Sectors are required");
        List<SectorDefinition> validated = List.copyOf(definitions);
        List<VenueSector> replacement = new ArrayList<>();
        for (SectorDefinition definition : validated) {
            VenueSector sector = definition.type() == SectorType.NUMBERED
                    ? VenueSector.numbered(this, definition.name(), definition.seats())
                    : VenueSector.generalAdmission(this, definition.name(), definition.capacity());
            replacement.add(sector);
        }
        sectors.clear();
        sectors.addAll(replacement);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public List<VenueSector> getSectors() {
        return Collections.unmodifiableList(sectors);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static BigDecimal coordinate(
            BigDecimal value,
            BigDecimal minimum,
            BigDecimal maximum,
            String field) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " is outside the allowed range");
        }
        return value;
    }
}
