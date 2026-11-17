package com.ticketya.queue.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Semántica de vencimiento de la ventana de admisión. */
class AdmissionWindowTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    private static OffsetDateTime at(String iso) {
        return OffsetDateTime.parse(iso);
    }

    @Test void futureWindowIsStillValid() {
        assertThat(AdmissionWindow.isExpired(at("2026-11-17T12:00:01Z"), NOW)).isFalse();
    }

    @Test void windowExpireExactlyAtTheDeadline() {
        assertThat(AdmissionWindow.isExpired(at("2026-11-17T12:00:00Z"), NOW)).isTrue();
    }

    @Test void pastWindowIsExpired() {
        assertThat(AdmissionWindow.isExpired(at("2026-11-17T11:59:59Z"), NOW)).isTrue();
    }

    @Test void missingWindowCountsAsExpired() {
        assertThat(AdmissionWindow.isExpired(null, NOW)).isTrue();
    }

    @Test void comparisonsWorkAcrossTimeZones() {
        OffsetDateTime sameDeadlineInOtherZone =
                OffsetDateTime.ofInstant(NOW.plusSeconds(30), ZoneOffset.of("-03:00"));
        assertThat(AdmissionWindow.isExpired(sameDeadlineInOtherZone, NOW)).isFalse();
    }
}
