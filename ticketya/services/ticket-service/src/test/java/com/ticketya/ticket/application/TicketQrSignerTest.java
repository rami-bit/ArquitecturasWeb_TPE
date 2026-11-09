package com.ticketya.ticket.application;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketQrSignerTest {
    private static final String SECRET = "a-local-test-signing-secret-with-32-bytes";

    @Test
    void createsStableVersionedPayloadWithValidHmacAndHash() throws Exception {
        TicketQrSigner signer = new TicketQrSigner(SECRET);
        UUID ticketId = UUID.randomUUID();

        String first = signer.payloadFor(ticketId);
        String second = signer.payloadFor(ticketId);
        String[] segments = first.split("\\.");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expectedSignature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(ticketId.toString().getBytes(StandardCharsets.UTF_8)));

        assertThat(first).isEqualTo(second).startsWith("v1." + ticketId + ".");
        assertThat(segments).hasSize(3);
        assertThat(segments[2]).isEqualTo(expectedSignature);
        assertThat(signer.hash(first)).hasSize(64).isEqualTo(signer.hash(second));
    }

    @Test
    void rejectsSigningSecretShorterThan256Bits() {
        assertThatThrownBy(() -> new TicketQrSigner("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 UTF-8 bytes");
    }
}
