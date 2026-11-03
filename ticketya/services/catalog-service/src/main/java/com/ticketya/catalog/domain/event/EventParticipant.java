package com.ticketya.catalog.domain.event;
import jakarta.persistence.*; import java.util.UUID;
@Entity @Table(name="event_participants")
public class EventParticipant {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="event_id",nullable=false) private Event event;
 @Column(nullable=false,length=200) private String name;
 protected EventParticipant(){} EventParticipant(Event e,String n){event=e;name=n;}
 public UUID getId(){return id;} public String getName(){return name;}
}
