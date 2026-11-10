package com.ticketya.inventory.application;

import com.ticketya.inventory.api.error.ApiProblemException;
import com.ticketya.inventory.api.reservation.ReservationItem;
import com.ticketya.inventory.api.reservation.ReservationRequest;
import com.ticketya.inventory.api.reservation.ReservationResponse;
import com.ticketya.inventory.infrastructure.persistence.InventoryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ReservationService {
    private final InventoryInitializationService initialization;
    private final ReservationExpiryService expiry;
    private final InventoryStore store;

    public ReservationService(InventoryInitializationService initialization,
                              ReservationExpiryService expiry,
                              InventoryStore store) {
        this.initialization = initialization;
        this.expiry = expiry;
        this.store = store;
    }

    public record ReservationResult(ReservationResponse reservation, boolean created) {
    }

    private record ValidatedItem(UUID sectorId, UUID seatId, int quantity) {
    }

    @Transactional(rollbackFor = Exception.class)
    public ReservationResult create(ReservationRequest request) {
        InventoryStore.InventoryRow inventory = initialization.ensure(request.eventId());
        if (store.isCancelled(request.eventId())) {
            throw ApiProblemException.conflict("The event was cancelled and no longer accepts reservations");
        }
        expiry.expireDueForEvent(inventory.id());

        String fingerprint = fingerprint(request);
        Optional<InventoryStore.ReservationRow> existing =
                store.findReservationByOrder(request.orderId());
        if (existing.isPresent()) {
            return existingResult(existing.get(), fingerprint);
        }

        List<ValidatedItem> items = validate(request, inventory);
        int ttlSeconds = request.ttlSeconds() == null
                ? inventory.holdTtlSeconds()
                : request.ttlSeconds();
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = createdAt.plusSeconds(ttlSeconds);
        UUID reservationId = UUID.randomUUID();

        int inserted = store.insertReservation(
                reservationId, request.orderId(), inventory.id(), fingerprint,
                expiresAt, createdAt);
        if (inserted == 0) {
            InventoryStore.ReservationRow concurrent = store.findReservationByOrder(
                            request.orderId())
                    .orElseThrow(() -> ApiProblemException.conflict(
                            "The orderId could not be reserved"));
            return existingResult(concurrent, fingerprint);
        }

        for (ValidatedItem item : items) {
            if (item.seatId() != null) {
                int held = store.holdSeat(item.seatId(), item.sectorId(), reservationId, expiresAt);
                if (held != 1) {
                    throw ApiProblemException.conflict(
                            "One or more requested seats are no longer available");
                }
                store.insertReservationItem(
                        UUID.randomUUID(), reservationId, item.sectorId(), item.seatId(), 1);
            } else {
                int reserved = store.reserveGeneral(item.sectorId(), item.quantity());
                if (reserved != 1) {
                    throw ApiProblemException.conflict(
                            "One or more requested sectors no longer have enough availability");
                }
                store.insertReservationItem(
                        UUID.randomUUID(), reservationId, item.sectorId(), null, item.quantity());
            }
        }

        InventoryStore.ReservationRow created = store.findReservationById(reservationId)
                .orElseThrow(() -> new IllegalStateException("Reservation was not persisted"));
        return new ReservationResult(toResponse(created), true);
    }

    public ReservationResponse byOrder(UUID orderId) {
        InventoryStore.ReservationRow reservation = store.findReservationByOrder(orderId)
                .orElseThrow(() -> ApiProblemException.notFound("Reservation not found"));
        return toResponse(reservation);
    }

    public ReservationResponse byId(UUID reservationId) {
        InventoryStore.ReservationRow reservation = store.findReservationById(reservationId)
                .orElseThrow(() -> ApiProblemException.notFound("Reservation not found"));
        return toResponse(reservation);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReservationResponse confirm(UUID reservationId) {
        InventoryStore.ReservationRow reservation = store.findReservationById(reservationId)
                .orElseThrow(() -> ApiProblemException.notFound("Reservation not found"));

        if ("CONFIRMED".equals(reservation.status())) {
            return toResponse(reservation);
        }
        if ("RELEASED".equals(reservation.status()) || "EXPIRED".equals(reservation.status())) {
            throw ApiProblemException.conflict(
                    "The reservation is not active and cannot be confirmed");
        }
        if (store.isCancelled(reservation.eventId())) {
            throw ApiProblemException.conflict("The event was cancelled and its reservations cannot be confirmed");
        }
        int transitioned = store.confirmReservation(reservationId);
        if (transitioned == 0) {
            InventoryStore.ReservationRow current = store.findReservationById(reservationId)
                    .orElseThrow(() -> ApiProblemException.notFound("Reservation not found"));
            if ("CONFIRMED".equals(current.status())) {
                return toResponse(current);
            }
            throw ApiProblemException.conflict(
                    "The reservation is no longer active and cannot be confirmed");
        }

        int expectedSeatItems = store.countSeatItems(reservationId);
        int soldSeats = store.sellItems(reservationId);
        if (soldSeats != expectedSeatItems) {
            throw ApiProblemException.conflict(
                    "The reservation seats could not be confirmed consistently");
        }

        int expectedGeneralItems = store.countGeneralItems(reservationId);
        int soldGeneral = store.sellGeneral(reservationId);
        if (soldGeneral != expectedGeneralItems) {
            throw ApiProblemException.conflict(
                    "The reservation general admission places could not be confirmed consistently");
        }

        return toResponse(store.findReservationById(reservationId)
                .orElseThrow(() -> ApiProblemException.notFound("Reservation not found")));
    }

    @Transactional(rollbackFor = Exception.class)
    public ReservationResponse release(UUID reservationId) {
        InventoryStore.ReservationRow reservation = store.findReservationById(reservationId)
                .orElseThrow(() -> ApiProblemException.notFound("Reservation not found"));

        if ("CONFIRMED".equals(reservation.status())) {
            throw ApiProblemException.conflict(
                    "A confirmed reservation cannot be released");
        }
        if ("RELEASED".equals(reservation.status()) || "EXPIRED".equals(reservation.status())) {
            return toResponse(reservation);
        }

        int transitioned = store.releaseReservation(reservationId);
        if (transitioned == 0) {
            InventoryStore.ReservationRow current = store.findReservationById(reservationId)
                    .orElseThrow(() -> ApiProblemException.notFound("Reservation not found"));
            if ("CONFIRMED".equals(current.status())) {
                throw ApiProblemException.conflict(
                        "A confirmed reservation cannot be released");
            }
            return toResponse(current);
        }

        store.releaseItems(reservationId);
        store.releaseGeneral(reservationId);
        return toResponse(store.findReservationById(reservationId)
                .orElseThrow(() -> ApiProblemException.notFound("Reservation not found")));
    }

    // -------------------------------------------------------------------- helpers

    private ReservationResult existingResult(InventoryStore.ReservationRow reservation,
                                             String fingerprint) {
        boolean samePayload = reservation.fingerprint().equals(fingerprint);
        boolean stillHolding = "ACTIVE".equals(reservation.status())
                || "CONFIRMED".equals(reservation.status());
        if (samePayload && stillHolding) {
            return new ReservationResult(toResponse(reservation), false);
        }
        throw ApiProblemException.conflict(
                "The orderId is already associated with a different reservation request");
    }

    private List<ValidatedItem> validate(ReservationRequest request,
                                         InventoryStore.InventoryRow inventory) {
        if (request.items() == null || request.items().isEmpty()) {
            throw ApiProblemException.badRequest("items must contain at least one element");
        }

        Set<UUID> seats = new HashSet<>();
        Set<UUID> generalSectors = new HashSet<>();
        List<ValidatedItem> validated = new ArrayList<>();

        for (var item : request.items()) {
            if (item == null || item.sectorId() == null) {
                throw ApiProblemException.badRequest("sectorId is required for every item");
            }
            boolean hasSeat = item.seatId() != null;
            boolean hasQuantity = item.quantity() != null;
            if (hasSeat == hasQuantity) {
                throw ApiProblemException.badRequest(
                        "Each item must contain exactly one of seatId or quantity");
            }

            InventoryStore.SectorRow sector = store.findSector(inventory.id(), item.sectorId())
                    .orElseThrow(() -> ApiProblemException.badRequest(
                            "The sector does not belong to the event"));

            if ("NUMBERED".equals(sector.type())) {
                if (!hasSeat) {
                    throw ApiProblemException.badRequest(
                            "Numbered sectors require a seatId");
                }
                if (!seats.add(item.seatId())) {
                    throw ApiProblemException.badRequest(
                            "The same seat cannot appear more than once");
                }
                store.findItem(sector.id(), item.seatId())
                        .orElseThrow(() -> ApiProblemException.badRequest(
                                "The seat does not belong to the sector"));
                validated.add(new ValidatedItem(sector.id(), item.seatId(), 1));
            } else if ("GENERAL_ADMISSION".equals(sector.type())) {
                if (hasSeat) {
                    throw ApiProblemException.badRequest(
                            "General admission sectors require quantity");
                }
                if (item.quantity() < 1) {
                    throw ApiProblemException.badRequest("quantity must be positive");
                }
                if (!generalSectors.add(sector.id())) {
                    throw ApiProblemException.badRequest(
                            "The same general admission sector cannot appear more than once");
                }
                validated.add(new ValidatedItem(sector.id(), null, item.quantity()));
            } else {
                throw ApiProblemException.badRequest("Unsupported sector type");
            }
        }
        return validated;
    }

    private ReservationResponse toResponse(InventoryStore.ReservationRow reservation) {
        List<ReservationItem> items = store.findReservationItems(reservation.id()).stream()
                .map(item -> new ReservationItem(
                        item.sectorId(), item.inventoryItemId(), item.quantity()))
                .toList();
        return new ReservationResponse(
                reservation.id(),
                reservation.orderId(),
                reservation.eventId(),
                reservation.status(),
                reservation.createdAt(),
                reservation.expiresAt(),
                items);
    }

    private static String fingerprint(ReservationRequest request) {
        String ttl = request.ttlSeconds() == null ? "" : request.ttlSeconds().toString();
        List<String> items = request.items() == null
                ? List.of()
                : request.items().stream()
                .map(item -> item.seatId() != null
                        ? "S|" + item.sectorId() + "|" + item.seatId()
                        : "G|" + item.sectorId() + "|" + item.quantity())
                .sorted()
                .toList();
        String canonical = request.eventId() + "|" + ttl + "|" + String.join(";", items);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
