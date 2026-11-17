package com.ticketya.queue.domain;

/**
 * Pure batch arithmetic for the lazy promotion of WAITING entries.
 *
 * The admitted threshold grows with the time the queue has been open:
 * {@code floor(elapsed / batchIntervalSeconds) * batchSize}, capped by the
 * total number of entries allocated for the event so positions that were
 * never handed out are never admitted.
 */
public final class BatchCalculator {

    private BatchCalculator() {
    }

    /**
     * Highest position that should be ADMITTED right now.
     *
     * @param elapsedSeconds  seconds since the queue was opened
     * @param intervalSeconds seconds between batch promotions
     * @param batchSize       entries promoted per interval
     * @param totalEntries    entries allocated so far for the event (cap)
     */
    public static long admittedThrough(long elapsedSeconds, long intervalSeconds,
                                       long batchSize, long totalEntries) {
        if (intervalSeconds <= 0 || batchSize <= 0) {
            throw new IllegalArgumentException("batch interval and size must be positive");
        }
        long elapsed = Math.max(0, elapsedSeconds);
        long batches = elapsed / intervalSeconds;
        long unbounded = batches * batchSize;
        return Math.min(unbounded, Math.max(0, totalEntries));
    }

    /** 1-based batch number an entry belongs to, derived only from its position. */
    public static long batchOf(long position, long batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batch size must be positive");
        }
        return Math.floorDiv(Math.max(1, position) - 1, batchSize) + 1;
    }
}
