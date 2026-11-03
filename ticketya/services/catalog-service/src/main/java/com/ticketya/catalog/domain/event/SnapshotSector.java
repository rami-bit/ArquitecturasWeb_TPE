package com.ticketya.catalog.domain.event;
import com.ticketya.catalog.domain.venue.*; import jakarta.persistence.*; import java.math.BigDecimal; import java.util.*;
@Entity @Table(name="snapshot_sectors")
public class SnapshotSector {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="snapshot_id",nullable=false) private EventSnapshot snapshot;
    @Column(name="source_sector_id",nullable=false) private UUID sourceSectorId;
    @Column(nullable=false,length=120) private String name;
    @Enumerated(EnumType.STRING) @Column(name="sector_type",nullable=false,length=32) private SectorType type;
    @Column(name="capacity",nullable=false) private int capacity;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal price;
    @OneToMany(mappedBy="sector",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("rowLabel ASC, seatNumber ASC") private List<SnapshotSeat> seats=new ArrayList<>();
    protected SnapshotSector(){}
    SnapshotSector(EventSnapshot snapshot, VenueSector source, BigDecimal price){this.snapshot=snapshot;sourceSectorId=source.getId();name=source.getName();type=source.getType();capacity=source.getCapacity();this.price=price;source.getSeats().forEach(s->seats.add(new SnapshotSeat(this,s)));}
    public UUID getId(){return id;} public UUID getSourceSectorId(){return sourceSectorId;} public String getName(){return name;} public SectorType getType(){return type;} public int getCapacity(){return capacity;} public BigDecimal getPrice(){return price;} public List<SnapshotSeat> getSeats(){return Collections.unmodifiableList(seats);}
}
