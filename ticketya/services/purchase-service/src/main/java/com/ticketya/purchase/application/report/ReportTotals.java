package com.ticketya.purchase.application.report;

import java.math.BigDecimal;

public record ReportTotals(
        Integer capacity,
        int soldTickets,
        int refundedTickets,
        int activeTickets,
        BigDecimal occupancyPercent,
        BigDecimal grossRevenue,
        BigDecimal refundedAmount,
        BigDecimal netRevenue) {
}
