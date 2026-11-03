package com.ticketya.catalog.domain.event;

import com.ticketya.catalog.domain.venue.Venue;
import com.ticketya.catalog.domain.venue.VenueSector;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name="event_snapshots", uniqueConstraints=@UniqueConstraint(name="uk_event_snapshot_event", columnNames="event_id"))
public class EventSnapshot {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="event_id", nullable=false, unique=true) private Event event;
    @Column(nullable=false, length=200) private String name;
    @Column(nullable=false, columnDefinition="text") private String description;
    @Column(name="event_type", nullable=false, length=80) private String eventType;
    @Column(name="starts_at", nullable=false) private OffsetDateTime startsAt;
    @Column(name="venue_name", nullable=false, length=200) private String venueName;
    @Column(name="venue_address", nullable=false, length=300) private String venueAddress;
    @Column(name="venue_city", nullable=false, length=120) private String venueCity;
    @Column(name="venue_latitude", nullable=false, precision=9, scale=6) private java.math.BigDecimal venueLatitude;
    @Column(name="venue_longitude", nullable=false, precision=9, scale=6) private java.math.BigDecimal venueLongitude;
    @OneToMany(mappedBy="snapshot", cascade=CascadeType.ALL, orphanRemoval=true) @OrderBy("name ASC") private List<SnapshotParticipant> participants=new ArrayList<>();
    @OneToMany(mappedBy="snapshot", cascade=CascadeType.ALL, orphanRemoval=true) @OrderBy("name ASC") private List<SnapshotSector> sectors=new ArrayList<>();
    protected EventSnapshot() {}
    private EventSnapshot(Event event) {
        this.event=event; name=event.getName(); description=event.getDescription(); eventType=event.getEventType(); startsAt=event.getStartsAt();
        Venue venue=event.getVenue(); venueName=venue.getName(); venueAddress=venue.getAddress(); venueCity=venue.getCity(); venueLatitude=venue.getLatitude(); venueLongitude=venue.getLongitude();
        event.getParticipants().forEach(p -> participants.add(new SnapshotParticipant(this,p.getName())));
        Map<UUID,java.math.BigDecimal> prices=new HashMap<>(); event.getOfferings().forEach(o -> prices.put(o.getVenueSectorId(),o.getPrice()));
        for(VenueSector sector:venue.getSectors()) if(prices.containsKey(sector.getId())) sectors.add(new SnapshotSector(this,sector,prices.get(sector.getId())));
    }
    public static EventSnapshot copyOf(Event event){return new EventSnapshot(event);}
    public UUID getId(){return id;} public Event getEvent(){return event;} public String getName(){return name;} public String getDescription(){return description;}
    public String getEventType(){return eventType;} public OffsetDateTime getStartsAt(){return startsAt;} public String getVenueName(){return venueName;}
    public String getVenueAddress(){return venueAddress;} public String getVenueCity(){return venueCity;} public java.math.BigDecimal getVenueLatitude(){return venueLatitude;}
    public java.math.BigDecimal getVenueLongitude(){return venueLongitude;} public List<SnapshotParticipant> getParticipants(){return Collections.unmodifiableList(participants);}
    public List<SnapshotSector> getSectors(){return Collections.unmodifiableList(sectors);}
}
