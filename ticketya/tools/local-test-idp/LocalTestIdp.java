import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * IdP de prueba local para la verificacion de {@code add-jwt-and-role-security}.
 *
 * <p>No forma parte del producto: es herramental de demostracion y verificacion,
 * disponible tambien como servicio local de Compose. Publica el JWKS
 * (clave publica RSA) del issuer, tal como lo haria el proveedor de identidad
 * externo configurado en {@code JWT_ISSUER_URI} / {@code JWT_JWK_SET_URI}, y
 * emite JWT RS256 con los claims acordados: {@code sub} UUID, {@code roles}
 * con exactamente un rol, {@code iss}, {@code iat} y {@code exp}.
 *
 * <p>Implementado solo con el JDK (mismo algoritmo que
 * {@code services/api-gateway/src/test/java/com/ticketya/gateway/contract/TestJwtIssuer.java})
 * para no agregar dependencias.
 *
 * <p>Expone dos endpoints:
 * <ul>
 *   <li>{@code GET /.well-known/jwks.json}: JWKS con la clave publica, que es
 *       lo que consume el Gateway mediante {@code JWT_JWK_SET_URI}.</li>
 *   <li>{@code GET /token?sub=<uuid>&role=<ROL>}: emite un JWT RS256 para esa
 *       identidad y devuelve {@code {"access_token": "...", "token_type":
 *       "Bearer", "expires_in": 3600}}. Lo usan los scripts de verificacion
 *       que necesitan identidades distintas por iteracion.</li>
 * </ul>
 *
 * <p>Uso desde la raiz del repositorio:
 * <pre>
 * java tools/local-test-idp/LocalTestIdp.java --port 9999 \
 *      --issuer http://host.docker.internal:9999 \
 *      --token organizer:e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b:ORGANIZER
 * </pre>
 *
 * <p>Cada opcion {@code --token nombre:sub:rol} emite un token y lo imprime
 * como {@code TOKEN <nombre> <jwt>} en stdout. El issuer debe coincidir
 * exactamente con {@code iss} del token porque el Gateway valida ese claim.
 *
 * <p>La opcion {@code --key-file <ruta>} persiste el par de claves para que
 * reiniciar el IdP no rote el JWKS: el Gateway cachea el JWKS que ya leyo y
 * los tokens emitidos despues dejarian de validar.
 */
public final class LocalTestIdp {

    private static final String KID = "ticketya-local-test-key";
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private LocalTestIdp() {
    }

    public static void main(String[] args) throws Exception {
        int port = 9999;
        String issuer = null;
        String keyFile = null;
        List<String[]> tokens = new ArrayList<>();

        for (int index = 0; index < args.length; index++) {
            switch (args[index]) {
                case "--port" -> port = Integer.parseInt(args[++index]);
                case "--issuer" -> issuer = args[++index];
                case "--key-file" -> keyFile = args[++index];
                case "--token" -> tokens.add(parseToken(args[++index]));
                default -> throw new IllegalArgumentException("Argumento desconocido: " + args[index]);
            }
        }

        if (issuer == null) {
            issuer = "http://localhost:" + port;
        }
        final String issuerUri = issuer;

        KeyPair keyPair = keyFile == null
                ? generateKeyPair()
                : loadOrCreateKeyPair(java.nio.file.Path.of(keyFile));
        byte[] jwks = jwksJson(keyPair).getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                String path = exchange.getRequestURI().getPath();
                if ("/token".equals(path)) {
                    byte[] payload = mint(exchange.getRequestURI().getRawQuery(), keyPair, issuerUri);
                    exchange.sendResponseHeaders(payload == null ? 400 : 200, payload.length);
                    if (payload != null) {
                        try (OutputStream output = exchange.getResponseBody()) {
                            output.write(payload);
                        }
                    }
                    return;
                }
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(jwks);
                }
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "local-test-idp");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();

        System.out.println("JWKS http://localhost:" + port + "/.well-known/jwks.json");
        System.out.println("ISSUER " + issuer);
        Instant expiresAt = Instant.now().plusSeconds(3600);
        for (String[] token : tokens) {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", token[1]);
            claims.put("roles", List.of(token[2]));
            claims.put("iss", issuer);
            claims.put("iat", Instant.now().getEpochSecond());
            claims.put("exp", expiresAt.getEpochSecond());
            System.out.println("TOKEN " + token[0] + " " + sign(keyPair, claims));
        }
        System.out.flush();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        Thread.currentThread().join();
    }

    private static String[] parseToken(String raw) {
        String[] parts = raw.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Formato esperado nombre:sub:rol, recibido: " + raw);
        }
        return parts;
    }

    /**
     * Emite un token para la identidad pedida en la query string
     * ({@code sub} y {@code role}) y lo devuelve como JSON de un endpoint de
     * token. Devuelve {@code null} si la query no trae ambos parametros.
     */
    private static byte[] mint(String rawQuery, KeyPair keyPair, String issuer) {
        Map<String, String> query = parseQuery(rawQuery);
        String sub = query.get("sub");
        String role = query.get("role");
        if (sub == null || sub.isBlank() || role == null || role.isBlank()) {
            return null;
        }
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", sub);
        claims.put("roles", List.of(role));
        claims.put("iss", issuer);
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());
        String payload = "{\"access_token\":\"" + sign(keyPair, claims)
                + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            values.put(java.net.URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                    java.net.URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return values;
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("RSA no disponible en el JDK", exception);
        }
    }

    /**
     * Carga el par de claves del archivo indicado o lo crea y persiste si no
     * existe. Persistir la clave evita que un reinicio del IdP invalide el
     * JWKS que el Gateway ya cacheo.
     */
    private static KeyPair loadOrCreateKeyPair(java.nio.file.Path path) {
        try {
            if (java.nio.file.Files.exists(path)) {
                String content = java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
                java.security.KeyFactory factory = java.security.KeyFactory.getInstance("RSA");
                java.security.PrivateKey privateKey = factory.generatePrivate(
                        new java.security.spec.PKCS8EncodedKeySpec(
                                decodePem(content, "PRIVATE KEY")));
                java.security.PublicKey publicKey = factory.generatePublic(
                        new java.security.spec.X509EncodedKeySpec(
                                decodePem(content, "PUBLIC KEY")));
                return new KeyPair(publicKey, privateKey);
            }
            KeyPair keyPair = generateKeyPair();
            java.nio.file.Path parent = path.getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            String pem = pem("PRIVATE KEY", keyPair.getPrivate().getEncoded())
                    + pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
            java.nio.file.Files.writeString(path, pem, StandardCharsets.UTF_8);
            return keyPair;
        } catch (GeneralSecurityException | java.io.IOException exception) {
            throw new IllegalStateException("No se pudo preparar la clave del IdP de prueba", exception);
        }
    }

    private static String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + label + "-----\n";
    }

    private static byte[] decodePem(String content, String label) {
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        int start = content.indexOf(begin);
        int finish = content.indexOf(end);
        if (start < 0 || finish < 0) {
            throw new IllegalArgumentException("El archivo de clave no contiene " + label);
        }
        String body = content.substring(start + begin.length(), finish).replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static String sign(KeyPair keyPair, Map<String, Object> claims) {
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

    private static String jwksJson(KeyPair keyPair) {
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
        throw new IllegalArgumentException("Claim no soportado: " + value.getClass());
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
