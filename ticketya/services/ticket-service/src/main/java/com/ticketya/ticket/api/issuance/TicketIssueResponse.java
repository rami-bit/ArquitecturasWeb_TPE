package com.ticketya.ticket.api.issuance;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TicketIssueResponse(
        UUID orderId,
        UUID orderItemId,
        List<TicketView> tickets) {

    public record TicketView(
            UUID ticketId,
            int unitNumber,
            UUID eventId,
            String eventName,
            OffsetDateTime eventStartsAt,
            String venueName,
            String venueCity,
            String sectorName,
            String rowLabel,
            Integer seatNumber,
            String status,
            OffsetDateTime issuedAt,
            String qrPayload,
            String qrPngBase64) {
    }
}
