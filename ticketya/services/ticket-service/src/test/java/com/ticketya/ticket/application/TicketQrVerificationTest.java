package com.ticketya.ticket.application;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Verificación del payload firmado leído en el acceso (US-F8). */
class TicketQrVerificationTest {
    private static final String SECRET = "a-local-test-signing-secret-with-32-bytes";
    private final TicketQrSigner signer = new TicketQrSigner(SECRET);

    @Test
    void acceptsOnlyPayloadsSignedWithTheConfiguredSecret() {
        UUID ticketId = UUID.randomUUID();
        String payload = signer.payloadFor(ticketId);

        assertThat(signer.verify(payload)).contains(ticketId);
        assertThat(signer.verify("  " + payload + " ")).contains(ticketId);

        TicketQrSigner otherKey = new TicketQrSigner("another-local-test-signing-secret-32-bytes");
        assertThat(otherKey.verify(payload)).isEmpty();
    }

    @Test
    void rejectsMalformedOrTamperedPayloads() {
        UUID ticketId = UUID.randomUUID();
        String payload = signer.payloadFor(ticketId);
        String signature = payload.split("\\.")[2];

        assertThat(signer.verify(null)).isEmpty();
        assertThat(signer.verify("")).isEmpty();
        assertThat(signer.verify("v1." + ticketId)).isEmpty();
        assertThat(signer.verify("v2." + ticketId + "." + signature)).isEmpty();
        assertThat(signer.verify("v1.not-a-uuid." + signature)).isEmpty();
        assertThat(signer.verify("v1." + UUID.randomUUID() + "." + signature)).isEmpty();
        assertThat(signer.verify("v1." + ticketId.toString().toUpperCase() + "." + signature)).isEmpty();
        assertThat(signer.verify(payload + ".extra")).isEmpty();
        assertThat(signer.verify(payload.substring(0, payload.length() - 1) + "x")).isEmpty();
    }
}
