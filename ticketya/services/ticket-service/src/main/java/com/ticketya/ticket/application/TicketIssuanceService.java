package com.ticketya.ticket.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.ticket.api.error.ApiProblemException;
import com.ticketya.ticket.api.issuance.TicketIssueRequest;
import com.ticketya.ticket.api.issuance.TicketIssueResponse;
import com.ticketya.ticket.api.issuance.TicketUnitRequest;
import com.ticketya.ticket.domain.Ticket;
import com.ticketya.ticket.domain.TicketIssuance;
import com.ticketya.ticket.infrastructure.persistence.TicketIssuanceRepository;
import com.ticketya.ticket.infrastructure.persistence.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class TicketIssuanceService {
    private final TicketIssuanceRepository issuanceRepository;
    private final TicketRepository ticketRepository;
    private final TicketQrSigner qrSigner;
    private final QrCodeRenderer qrCodeRenderer;
    private final ObjectMapper objectMapper;

    public TicketIssuanceService(
            TicketIssuanceRepository issuanceRepository,
            TicketRepository ticketRepository,
            TicketQrSigner qrSigner,
            QrCodeRenderer qrCodeRenderer,
            ObjectMapper objectMapper) {
        this.issuanceRepository = issuanceRepository;
        this.ticketRepository = ticketRepository;
        this.qrSigner = qrSigner;
        this.qrCodeRenderer = qrCodeRenderer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IssueResult issue(UUID orderId, UUID orderItemId, TicketIssueRequest request) {
        validateSeatData(request.tickets());
        String fingerprint = fingerprint(request);
        UUID issuanceId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        int inserted = issuanceRepository.insertIfAbsent(
                issuanceId, orderId, orderItemId, fingerprint, now);
        if (inserted == 1) {
            persistTickets(issuanceId, request, now);
        } else {
            TicketIssuance existing = issuanceRepository
                    .findByOrderIdAndOrderItemId(orderId, orderItemId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Ticket issuance conflict was not visible after insert"));
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw ApiProblemException.conflict(
                        "This order item was already issued with different ticket details");
            }
        }

        TicketIssuance issuance = issuanceRepository
                .findByOrderIdAndOrderItemId(orderId, orderItemId)
                .orElseThrow(() -> new IllegalStateException(
                        "Ticket issuance was not persisted"));
        List<Ticket> tickets = ticketRepository
                .findByIssuanceIdOrderByUnitNumberAsc(issuance.getId());
        if (tickets.size() != request.tickets().size()) {
            throw new IllegalStateException(
                    "Persisted ticket count does not match the issuance request");
        }
        return new IssueResult(inserted == 1, toResponse(orderId, orderItemId, tickets));
    }

    private void persistTickets(
            UUID issuanceId, TicketIssueRequest request, OffsetDateTime issuedAt) {
        List<Ticket> tickets = request.tickets().stream()
                .sorted(Comparator.comparingInt(TicketUnitRequest::unitNumber))
                .map(unit -> {
                    UUID ticketId = UUID.randomUUID();
                    String payload = qrSigner.payloadFor(ticketId);
                    return new Ticket(
                            ticketId,
                            issuanceId,
                            unit.unitNumber(),
                            request.customerId(),
                            request.eventId(),
                            request.eventName(),
                            request.eventStartsAt(),
                            request.venueName(),
                            request.venueCity(),
                            request.sectorName(),
                            unit.rowLabel(),
                            unit.seatNumber(),
                            qrSigner.hash(payload),
                            issuedAt);
                })
                .toList();
        ticketRepository.saveAllAndFlush(tickets);
    }

    private TicketIssueResponse toResponse(
            UUID orderId, UUID orderItemId, List<Ticket> tickets) {
        List<TicketIssueResponse.TicketView> views = tickets.stream()
                .map(ticket -> {
                    String payload = qrSigner.payloadFor(ticket.getId());
                    return new TicketIssueResponse.TicketView(
                            ticket.getId(),
                            ticket.getUnitNumber(),
                            ticket.getEventId(),
                            ticket.getEventName(),
                            ticket.getEventStartsAt(),
                            ticket.getVenueName(),
                            ticket.getVenueCity(),
                            ticket.getSectorName(),
                            ticket.getRowLabel(),
                            ticket.getSeatNumber(),
                            ticket.getStatus().name(),
                            ticket.getIssuedAt(),
                            payload,
                            qrCodeRenderer.renderPngBase64(payload));
                })
                .toList();
        return new TicketIssueResponse(orderId, orderItemId, views);
    }

    private void validateSeatData(List<TicketUnitRequest> units) {
        boolean hasDuplicateUnits = units.stream()
                .map(TicketUnitRequest::unitNumber)
                .distinct()
                .count() != units.size();
        if (hasDuplicateUnits) {
            throw ApiProblemException.badRequest("Ticket unit numbers must be unique");
        }

        boolean hasIncompleteSeat = units.stream()
                .anyMatch(unit -> (unit.rowLabel() == null) != (unit.seatNumber() == null)
                        || (unit.rowLabel() != null && unit.rowLabel().isBlank()));
        if (hasIncompleteSeat) {
            throw ApiProblemException.badRequest(
                    "A numbered seat must include both rowLabel and seatNumber");
        }
    }

    private String fingerprint(TicketIssueRequest request) {
        CanonicalTicketRequest canonical = new CanonicalTicketRequest(
                request.customerId(),
                request.eventId(),
                request.eventName(),
                request.eventStartsAt(),
                request.venueName(),
                request.venueCity(),
                request.sectorName(),
                request.tickets().stream()
                        .sorted(Comparator.comparingInt(TicketUnitRequest::unitNumber))
                        .toList());
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(canonical);
            return qrSigner.hash(serialized);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to fingerprint ticket issuance", exception);
        }
    }

    private record CanonicalTicketRequest(
            UUID customerId,
            UUID eventId,
            String eventName,
            OffsetDateTime eventStartsAt,
            String venueName,
            String venueCity,
            String sectorName,
            List<TicketUnitRequest> tickets) {
    }

    public record IssueResult(boolean created, TicketIssueResponse response) {
    }
}
