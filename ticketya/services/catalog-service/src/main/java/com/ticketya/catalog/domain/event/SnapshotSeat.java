package com.ticketya.catalog.domain.event;
import com.ticketya.catalog.domain.venue.VenueSeat; import jakarta.persistence.*; import java.util.UUID;
@Entity @Table(name="snapshot_seats",uniqueConstraints=@UniqueConstraint(name="uk_snapshot_seat_position",columnNames={"sector_id","row_label","seat_number"}))
public class SnapshotSeat {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sector_id",nullable=false) private SnapshotSector sector;
    @Column(name="source_seat_id",nullable=false) private UUID sourceSeatId;
    @Column(name="row_label",nullable=false,length=40) private String rowLabel;
    @Column(name="seat_number",nullable=false) private int seatNumber;
    protected SnapshotSeat(){}
    SnapshotSeat(SnapshotSector sector, VenueSeat seat){this.sector=sector;sourceSeatId=seat.getId();rowLabel=seat.getRowLabel();seatNumber=seat.getSeatNumber();}
    public UUID getId(){return id;} public UUID getSourceSeatId(){return sourceSeatId;} public String getRowLabel(){return rowLabel;} public int getSeatNumber(){return seatNumber;}
}
