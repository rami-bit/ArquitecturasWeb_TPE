package com.ticketya.queue.domain;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Admission window predicate. Mirrors the SQL sweep
 * ({@code admission_expires_at <= now()}) as a belt-and-braces check before
 * an admission token is accepted.
 */
public final class AdmissionWindow {

    private AdmissionWindow() {
    }

    /** {@code true} when the window is missing or no longer in the future. */
    public static boolean isExpired(OffsetDateTime expiresAt, Instant now) {
        return expiresAt == null || !expiresAt.toInstant().isAfter(now);
    }
}
