package com.ticketya.catalog.domain.event;
import jakarta.persistence.*; import java.util.UUID;
@Entity @Table(name="snapshot_participants")
public class SnapshotParticipant {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="snapshot_id",nullable=false) private EventSnapshot snapshot;
    @Column(nullable=false,length=200) private String name;
    protected SnapshotParticipant(){}
    SnapshotParticipant(EventSnapshot snapshot,String name){this.snapshot=snapshot;this.name=name;}
    public UUID getId(){return id;} public String getName(){return name;}
}
