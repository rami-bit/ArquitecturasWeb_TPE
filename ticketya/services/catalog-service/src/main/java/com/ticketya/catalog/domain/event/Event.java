package com.ticketya.catalog.domain.event;

import com.ticketya.catalog.domain.venue.Venue;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name="events")
public class Event {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false,length=200) private String name;
    @Column(nullable=false,columnDefinition="text") private String description;
    @Column(name="event_type",nullable=false,length=80) private String eventType;
    @Column(name="starts_at",nullable=false) private OffsetDateTime startsAt;
    @Column(name="organizer_id",nullable=false) private UUID organizerId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="venue_id",nullable=false) private Venue venue;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private EventStatus status=EventStatus.DRAFT;
    @OneToMany(mappedBy="event",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("name ASC") private List<EventParticipant> participants=new ArrayList<>();
    @OneToMany(mappedBy="event",cascade=CascadeType.ALL,orphanRemoval=true) private List<EventSectorOffering> offerings=new ArrayList<>();
    @OneToOne(mappedBy="event", fetch=FetchType.LAZY, cascade=CascadeType.ALL, orphanRemoval=true) private EventSnapshot snapshot;
    protected Event() {}
    public Event(String name,String description,String eventType,OffsetDateTime startsAt,UUID organizerId,Venue venue) {
        this.organizerId=Objects.requireNonNull(organizerId); this.venue=Objects.requireNonNull(venue); update(name,description,eventType,startsAt);
    }
    public void update(String name,String description,String eventType,OffsetDateTime startsAt) {
        this.name=required(name,"Event name"); this.description=required(description,"Event description");
        this.eventType=required(eventType,"Event type"); this.startsAt=Objects.requireNonNull(startsAt,"Event start time is required");
    }
    public void setVenue(Venue venue){this.venue=Objects.requireNonNull(venue);}
    public void clearOfferings(){offerings.clear();}
    public void replaceParticipants(Collection<String> names){
        if(names==null) throw new IllegalArgumentException("Participants are required");
        var cleaned=names.stream().map(n->required(n,"Participant name")).toList();
        if(new HashSet<>(cleaned).size()!=cleaned.size()) throw new IllegalArgumentException("Participant names must be unique");
        participants.clear(); cleaned.forEach(n->participants.add(new EventParticipant(this,n)));
    }
    public void replaceOfferings(Map<UUID,java.math.BigDecimal> prices){
        if(prices==null) throw new IllegalArgumentException("Offerings are required");
        offerings.clear(); prices.forEach((sector,price)->offerings.add(new EventSectorOffering(this,sector,price)));
    }
    public void setStatus(EventStatus status){this.status=Objects.requireNonNull(status);}
    public void setSnapshot(EventSnapshot snapshot){this.snapshot=Objects.requireNonNull(snapshot);}
    private static String required(String s,String field){if(s==null||s.isBlank())throw new IllegalArgumentException(field+" is required");return s.trim();}
    public UUID getId(){return id;} public String getName(){return name;} public String getDescription(){return description;}
    public String getEventType(){return eventType;} public OffsetDateTime getStartsAt(){return startsAt;} public UUID getOrganizerId(){return organizerId;}
    public Venue getVenue(){return venue;} public EventStatus getStatus(){return status;} public List<EventParticipant> getParticipants(){return Collections.unmodifiableList(participants);}
    public List<EventSectorOffering> getOfferings(){return Collections.unmodifiableList(offerings);}
    public EventSnapshot getSnapshot(){return snapshot;}
}
