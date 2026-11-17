package com.ticketya.queue.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generación y verificación del token de admisión: válido, de otro cliente,
 * de otro evento, de otra entrada, de otro instante y manipulado.
 */
class AdmissionTokenCodecTest {

    private static final String SECRET = "test-admission-secret";
    private static final UUID ENTRY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CLIENT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant ISSUED_AT = Instant.parse("2026-11-17T12:00:00.750Z");

    @Test void tokenIsDeterministicForTheSameInputs() {
        String first = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT);
        String second = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT);
        assertThat(first).isEqualTo(second);
    }

    @Test void issuedAtIsTruncatedToSeconds() {
        String fromFractional = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT);
        String fromWhole = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT,
                Instant.parse("2026-11-17T12:00:00Z"));
        assertThat(fromFractional).isEqualTo(fromWhole);
    }

    @Test void tokenIsBase64UrlWithoutPadding() {
        String token = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT);
        assertThat(token).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        assertThat(Base64.getUrlDecoder().decode(token)).isNotEmpty();
    }

    @Test void validTokenVerifies() {
        String token = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT);
        assertThat(AdmissionTokenCodec.verify(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT, token))
                .isTrue();
        assertThat(AdmissionTokenCodec.verify(SECRET, ENTRY, EVENT, CLIENT,
                Instant.parse("2026-11-17T12:00:00.999Z"), token)).isTrue();
    }

    @Test void tokenOfAnotherClientIsRejected() {
        String token = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT);
        assertThat(AdmissionTokenCodec.verify(SECRET, ENTRY, EVENT,
                UUID.fromString("44444444-4444-4444-4444-444444444444"), ISSUED_AT, token)).isFalse();
    }

    @Test void tokenOfAnotherEventIsRejected() {
        String token = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT);
        assertThat(AdmissionTokenCodec.verify(SECRET, ENTRY,
                UUID.fromString("55555555-5555-5555-5555-555555555555"), CLIENT, ISSUED_AT, token))
                .isFalse();
    }

    @Test void tokenOfAnotherEntryIsRejected() {
        String token = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT);
        assertThat(AdmissionTokenCodec.verify(SECRET,
                UUID.fromString("66666666-6666-6666-6666-666666666666"), EVENT, CLIENT, ISSUED_AT, token))
                .isFalse();
    }

    @Test void tokenOfAnotherInstantIsRejected() {
        String token = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT);
        assertThat(AdmissionTokenCodec.verify(SECRET, ENTRY, EVENT, CLIENT,
                ISSUED_AT.plusSeconds(1), token)).isFalse();
    }

    @Test void tamperedTokenIsRejected() {
        String token = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT);
        char flipped = token.charAt(0) == 'A' ? 'B' : 'A';
        String tampered = flipped + token.substring(1);
        assertThat(AdmissionTokenCodec.verify(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT, tampered))
                .isFalse();
    }

    @Test void blankOrMissingTokenIsRejected() {
        assertThat(AdmissionTokenCodec.verify(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT, null))
                .isFalse();
        assertThat(AdmissionTokenCodec.verify(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT, "  "))
                .isFalse();
    }

    @Test void aDifferentSecretDoesNotVerify() {
        String token = AdmissionTokenCodec.encode(SECRET, ENTRY, EVENT, CLIENT, ISSUED_AT);
        assertThat(AdmissionTokenCodec.verify("other-secret", ENTRY, EVENT, CLIENT, ISSUED_AT, token))
                .isFalse();
    }
}
