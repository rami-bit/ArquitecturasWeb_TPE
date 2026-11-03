package com.ticketya.catalog.domain.event;
import jakarta.persistence.*; import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="event_sector_offerings")
public class EventSectorOffering {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="event_id",nullable=false) private Event event;
 @Column(name="venue_sector_id",nullable=false) private UUID venueSectorId;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal price;
 protected EventSectorOffering(){} EventSectorOffering(Event e,UUID sector,BigDecimal price){if(sector==null)throw new IllegalArgumentException("Sector is required");if(price==null||price.signum()<=0)throw new IllegalArgumentException("Offering price must be positive");event=e;venueSectorId=sector;this.price=price;}
 public UUID getId(){return id;} public UUID getVenueSectorId(){return venueSectorId;} public BigDecimal getPrice(){return price;}
}
