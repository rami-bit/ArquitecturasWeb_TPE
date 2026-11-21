package com.ticketya.notification.application;

/** Espera exponencial acotada entre intentos: base, 2·base, 4·base… hasta max. */
public final class RetryPolicy {
    private RetryPolicy() {
    }

    public static long delayMs(int attempt, long baseDelayMs, long maxDelayMs) {
        if (attempt < 1) {
            return 0;
        }
        long delay = baseDelayMs;
        for (int i = 1; i < attempt && delay < maxDelayMs; i++) {
            delay *= 2;
        }
        return Math.min(delay, maxDelayMs);
    }
}
