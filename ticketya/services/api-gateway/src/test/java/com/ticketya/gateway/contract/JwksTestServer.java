package com.ticketya.gateway.contract;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Servidor HTTP de prueba que expone el JWKS de un {@link TestJwtIssuer} en
 * {@code /.well-known/jwks.json}, simulando el endpoint de claves del IdP
 * externo configurado para el Gateway. Sólo para tests; no se declara en el
 * POM y no forma parte del producto.
 */
public final class JwksTestServer implements AutoCloseable {

    private final HttpServer server;

    private JwksTestServer(HttpServer server) {
        this.server = server;
    }

    public static JwksTestServer start(TestJwtIssuer issuer) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] jwks = issuer.jwksJson().getBytes(StandardCharsets.UTF_8);
            server.createContext("/", exchange -> {
                try (exchange) {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, jwks.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(jwks);
                    }
                }
            });
            server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "jwks-test-stub");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            return new JwksTestServer(server);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo levantar el JWKS de prueba", exception);
        }
    }

    /** URL base del issuer de prueba (el JWKS queda en /.well-known/jwks.json). */
    public String issuerUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
