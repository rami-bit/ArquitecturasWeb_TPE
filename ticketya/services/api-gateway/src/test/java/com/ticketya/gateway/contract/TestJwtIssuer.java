package com.ticketya.gateway.contract;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emisor de JWT RS256 para pruebas, implementado sólo con el JDK: genera un par
 * de claves RSA efímero, firma tokens compactos JWS (header + payload + firma
 * PKCS#1 v1.5 SHA-256) y expone el JWKS de la clave pública. Permite simular un
 * IdP externo controlado (claves propias e issuer configurable) sin agregar
 * dependencias de test al POM.
 *
 * <p>Modelo de claims acordado para TicketYa: {@code sub} UUID del usuario,
 * {@code roles} lista con exactamente un valor permitido, más {@code iss},
 * {@code iat} y {@code exp} estándar.
 */
public final class TestJwtIssuer {

    private static final String KID = "ticketya-test-key";
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private final KeyPair keyPair;

    public TestJwtIssuer() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("RSA no disponible en el JDK", exception);
        }
    }

    /** JWT RS256 compacto con exactamente los claims indicados. */
    public String jwt(Map<String, Object> claims) {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + KID + "\"}";
        String signingInput = BASE64URL.encodeToString(header.getBytes(StandardCharsets.UTF_8))
                + "."
                + BASE64URL.encodeToString(json(claims).getBytes(StandardCharsets.UTF_8));
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(keyPair.getPrivate());
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + BASE64URL.encodeToString(signer.sign());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo firmar el JWT de prueba", exception);
        }
    }

    /**
     * Token con los claims estándar del contrato: {@code sub} UUID, un único rol
     * en {@code roles}, issuer y vigencia. Los parámetros {@code null} omiten el
     * claim correspondiente (útil para los casos de claims inválidos).
     */
    public String token(String issuer, String sub, List<String> roles, Instant expiresAt) {
        Map<String, Object> claims = new LinkedHashMap<>();
        if (sub != null) {
            claims.put("sub", sub);
        }
        if (roles != null) {
            claims.put("roles", roles);
        }
        if (issuer != null) {
            claims.put("iss", issuer);
        }
        if (expiresAt != null) {
            claims.put("iat", expiresAt.getEpochSecond() - 30);
            claims.put("exp", expiresAt.getEpochSecond());
        }
        return jwt(claims);
    }

    /** Token válido por defecto: 10 minutos de vigencia y un único rol. */
    public String bearerToken(String issuer, String sub, String role) {
        return token(issuer, sub, List.of(role), Instant.now().plusSeconds(600));
    }

    /** JWKS (RS256) con la clave pública de este emisor. */
    public String jwksJson() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\""
                + KID + "\",\"n\":\"" + base64UrlUnsigned(publicKey.getModulus())
                + "\",\"e\":\"" + base64UrlUnsigned(publicKey.getPublicExponent()) + "\"}]}";
    }

    private static String base64UrlUnsigned(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray();
        int first = (bytes.length > 1 && bytes[0] == 0) ? 1 : 0;
        return BASE64URL.encodeToString(Arrays.copyOfRange(bytes, first, bytes.length));
    }

    /** Serializador JSON mínimo, suficiente para claims controlados por los tests. */
    private static String json(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return quote(string);
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            for (Object item : list) {
                items.add(json(item));
            }
            return "[" + String.join(",", items) + "]";
        }
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entries.add(quote(String.valueOf(entry.getKey())) + ":" + json(entry.getValue()));
            }
            return "{" + String.join(",", entries) + "}";
        }
        throw new IllegalArgumentException(
                "Claim no soportado por el emisor de prueba: " + value.getClass());
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append("\"").toString();
    }
}
