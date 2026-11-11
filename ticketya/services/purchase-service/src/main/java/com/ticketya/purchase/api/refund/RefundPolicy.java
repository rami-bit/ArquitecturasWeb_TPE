package com.ticketya.purchase.api.refund;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;

/** Pure business rules shared by refund request and decision flows. */
final class RefundPolicy {
    private static final Duration REFUND_CUTOFF = Duration.ofHours(72);

    private RefundPolicy() {
    }

    static boolean isWithinRequestWindow(OffsetDateTime eventStartsAt, Instant now) {
        return eventStartsAt.toInstant().isAfter(now.plus(REFUND_CUTOFF));
    }

    static boolean isValidDecision(String decision) {
        return "APPROVED".equals(decision) || "REJECTED".equals(decision);
    }

    static boolean canSetDecision(String currentDecision, String requestedDecision) {
        return currentDecision == null || currentDecision.equals(requestedDecision);
    }
}
