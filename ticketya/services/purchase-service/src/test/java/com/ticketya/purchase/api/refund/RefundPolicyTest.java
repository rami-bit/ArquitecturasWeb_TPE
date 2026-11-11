package com.ticketya.purchase.api.refund;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundPolicyTest {
    private static final Instant NOW = Instant.parse("2027-01-01T12:00:00Z");

    @Test
    void requestWindowRequiresEventToStartMoreThan72HoursAhead() {
        assertTrue(RefundPolicy.isWithinRequestWindow(
                OffsetDateTime.ofInstant(NOW.plusSeconds(72 * 60 * 60 + 1), ZoneOffset.UTC), NOW));
        assertFalse(RefundPolicy.isWithinRequestWindow(
                OffsetDateTime.ofInstant(NOW.plusSeconds(72 * 60 * 60), ZoneOffset.UTC), NOW));
        assertFalse(RefundPolicy.isWithinRequestWindow(
                OffsetDateTime.ofInstant(NOW.plusSeconds(72 * 60 * 60 - 1), ZoneOffset.UTC), NOW));
    }

    @Test
    void requestWindowComparesInstantsAcrossOffsets() {
        OffsetDateTime equivalentCutoffWithDifferentOffset = OffsetDateTime.parse("2027-01-04T10:00:00-02:00");
        assertFalse(RefundPolicy.isWithinRequestWindow(equivalentCutoffWithDifferentOffset, NOW));
    }

    @Test
    void onlyApprovedAndRejectedAreValidDecisions() {
        assertTrue(RefundPolicy.isValidDecision("APPROVED"));
        assertTrue(RefundPolicy.isValidDecision("REJECTED"));
        assertFalse(RefundPolicy.isValidDecision(null));
        assertFalse(RefundPolicy.isValidDecision("PENDING"));
        assertFalse(RefundPolicy.isValidDecision("approved"));
    }

    @Test
    void decisionCanBeRepeatedButCannotBeChanged() {
        assertTrue(RefundPolicy.canSetDecision(null, "APPROVED"));
        assertTrue(RefundPolicy.canSetDecision("REJECTED", "REJECTED"));
        assertFalse(RefundPolicy.canSetDecision("APPROVED", "REJECTED"));
    }
}
