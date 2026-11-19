package com.ticketya.purchase.application.report;

import java.math.BigDecimal;
import java.util.UUID;

/** Entradas devueltas con reintegro completado y su importe, por sector. */
public record SectorRefunds(UUID sourceSectorId, int refundedTickets, BigDecimal refundedAmount) {
}
