package com.ticketya.queue.domain;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Stateless admission token: {@code base64url(HMAC-SHA256(secret,
 * entryId|eventId|clientId|issuedAtEpochSeconds))}. The token is never
 * persisted; it is recomputed from the stored {@code admitted_at} truncated
 * to seconds, so the client can retrieve it again on every poll while the
 * entry stays ADMITTED.
 */
public final class AdmissionTokenCodec {

    private static final String ALGORITHM = "HmacSHA256";

    private AdmissionTokenCodec() {
    }

    /**
     * Recomputes the token of an admission. {@code issuedAt} is truncated to
     * seconds, matching the persistence granularity of {@code admitted_at}.
     */
    public static String encode(String secret, UUID entryId, UUID eventId,
                                UUID clientId, Instant issuedAt) {
        byte[] mac = hmac(secret, payload(entryId, eventId, clientId, issuedAt));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
    }

    /** Constant-time verification of a provided token against the expected one. */
    public static boolean verify(String secret, UUID entryId, UUID eventId,
                                 UUID clientId, Instant issuedAt, String providedToken) {
        if (providedToken == null || providedToken.isBlank()) {
            return false;
        }
        byte[] expected = encode(secret, entryId, eventId, clientId, issuedAt)
                .getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedToken.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }

    private static byte[] hmac(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException failure) {
            throw new IllegalStateException("HMAC-SHA256 is not available", failure);
        }
    }

    private static byte[] payload(UUID entryId, UUID eventId, UUID clientId, Instant issuedAt) {
        Instant issued = issuedAt.truncatedTo(ChronoUnit.SECONDS);
        String value = entryId + "|" + eventId + "|" + clientId + "|" + issued.getEpochSecond();
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
