package com.ticketya.ticket.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class TicketQrSigner {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";
    private final byte[] signingSecret;

    public TicketQrSigner(@Value("${ticket.qr.signing-secret}") String signingSecret) {
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
        if (this.signingSecret.length < 32) {
            throw new IllegalStateException(
                    "TICKET_QR_SIGNING_SECRET must contain at least 32 UTF-8 bytes");
        }
    }

    public String payloadFor(UUID ticketId) {
        String id = ticketId.toString();
        return "v1." + id + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sign(id));
    }

    public String hash(String payload) {
        return hash(payload.getBytes(StandardCharsets.UTF_8));
    }

    public String hash(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM)
                    .digest(value);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign ticket QR payload", exception);
        }
    }
}
