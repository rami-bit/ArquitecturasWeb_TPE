package com.ticketya.inventory.application;

import com.ticketya.inventory.api.availability.AvailabilityResponse;
import com.ticketya.inventory.api.availability.SeatAvailability;
import com.ticketya.inventory.api.availability.SectorAvailability;
import com.ticketya.inventory.infrastructure.persistence.InventoryStore;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AvailabilityService {
    private final InventoryInitializationService initialization;
    private final ReservationExpiryService expiry;
    private final InventoryStore store;

    public AvailabilityService(InventoryInitializationService initialization,
                               ReservationExpiryService expiry,
                               InventoryStore store) {
        this.initialization = initialization;
        this.expiry = expiry;
        this.store = store;
    }

    public AvailabilityResponse availability(UUID eventId) {
        InventoryStore.InventoryRow inventory = initialization.ensure(eventId);
        expiry.expireDueForEvent(inventory.id());

        List<SectorAvailability> sectors = new ArrayList<>();
        for (InventoryStore.SectorRow sector : store.findSectors(inventory.id())) {
            if ("NUMBERED".equals(sector.type())) {
                List<InventoryStore.ItemRow> items = store.findItems(sector.id());
                int sold = (int) items.stream().filter(i -> "SOLD".equals(i.status())).count();
                int reserved = (int) items.stream().filter(i -> "HELD".equals(i.status())).count();
                List<SeatAvailability> seats = items.stream()
                        .map(item -> new SeatAvailability(
                                item.id(), item.sourceSeatId(), item.rowLabel(), item.seatNumber(), item.status()))
                        .toList();
                sectors.add(new SectorAvailability(
                        sector.id(),
                        sector.sourceSectorId(),
                        sector.name(),
                        sector.type(),
                        sector.capacity(),
                        sold,
                        reserved,
                        sector.capacity() - sold - reserved,
                        seats));
            } else {
                sectors.add(new SectorAvailability(
                        sector.id(),
                        sector.sourceSectorId(),
                        sector.name(),
                        sector.type(),
                        sector.capacity(),
                        sector.sold(),
                        sector.reserved(),
                        sector.capacity() - sector.sold() - sector.reserved(),
                        null));
            }
        }
        return new AvailabilityResponse(
                eventId, OffsetDateTime.now(ZoneOffset.UTC), sectors);
    }
}
