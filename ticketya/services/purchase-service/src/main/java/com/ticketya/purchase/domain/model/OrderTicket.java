package com.ticketya.purchase.domain.model;

import java.util.UUID;

/** Ticket emitido para una unidad de ítem. */
public record OrderTicket(
        UUID id,
        UUID orderId,
        UUID orderItemId,
        UUID ticketId,
        int unitNumber,
        String status,
        String qrPayload,
        String qrPngBase64,
        String rowLabel,
        Integer seatNumber) {
}
