package com.ticketya.payment.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.payment.PaymentMockApplication;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base de la suite de contrato de Payment Mock (caja negra).
 *
 * Sólo referencia la clase de aplicación pública {@code PaymentMockApplication} y
 * APIs de Spring / JUnit / Testcontainers / Jackson: ningún DTO, controlador,
 * repositorio ni servicio interno del builder.
 *
 * Levanta UNA sola base PostgreSQL por JVM; cada test usa un {@code orderId}
 * aleatorio propio para no depender de limpieza de base.
 */
@SpringBootTest(
        classes = PaymentMockApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class PaymentContractTestBase {

    protected static final String CORRELATION_HEADER = "X-Correlation-Id";

    protected static final ObjectMapper JSON = new ObjectMapper();

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine");

    private static volatile boolean postgresStarted;

    @LocalServerPort
    protected int port;

    /** Cliente HTTP propio (sin depender del auto-config del contexto). */
    private final TestRestTemplate rest = new TestRestTemplate();

    @BeforeAll
    static synchronized void startPostgresOnceForTheWholeJvm() {
        if (!postgresStarted) {
            POSTGRES.start();
            postgresStarted = true;
        }
    }

    @DynamicPropertySource
    static void paymentContractProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // El puerto real lo entrega @LocalServerPort; SERVER_PORT=0 evita fijar 8085.
        registry.add("SERVER_PORT", () -> "0");
        registry.add("server.port", () -> "0");
    }

    // ------------------------------------------------------------------ helpers HTTP

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected ResponseEntity<String> get(String path) {
        return exchange(HttpMethod.GET, path, null, Map.of());
    }

    protected ResponseEntity<String> get(String path, String correlationId) {
        return exchange(HttpMethod.GET, path, null, Map.of(CORRELATION_HEADER, correlationId));
    }

    protected ResponseEntity<String> post(String path, String body) {
        return exchange(HttpMethod.POST, path, body, Map.of());
    }

    protected ResponseEntity<String> post(String path, String body, String correlationId) {
        return exchange(HttpMethod.POST, path, body,
                Map.of(CORRELATION_HEADER, correlationId));
    }

    protected ResponseEntity<String> exchange(
            HttpMethod method, String path, String body, Map<String, String> headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(httpHeaders::set);
        return rest.exchange(url(path), method, new HttpEntity<>(body, httpHeaders), String.class);
    }

    /** POST sin cuerpo, para los comandos como {@code /refund}. */
    protected ResponseEntity<String> postWithoutBody(String path) {
        return exchange(HttpMethod.POST, path, null, Map.of());
    }

    // ------------------------------------------------------------- helpers de dominio

    protected static String paymentBody(String orderId, double amount, String currency,
                                        String method, String token) {
        return String.format(Locale.ROOT,
                "{\"orderId\":\"%s\",\"amount\":%.2f,\"currency\":\"%s\",\"method\":\"%s\","
                        + "\"token\":\"%s\"}",
                orderId, amount, currency, method, token);
    }

    protected static String newOrderId() {
        return UUID.randomUUID().toString();
    }

    // --------------------------------------------------------------- helpers assert

    protected static void assertProblem(ResponseEntity<String> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        MediaType contentType = response.getHeaders().getContentType();
        assertThat(contentType).as("los errores usan application/problem+json").isNotNull();
        assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .as("content-type %s es problem+json", contentType)
                .isTrue();
        JsonNode problem = parseBody(response);
        assertThat(problem.path("type").asText(null))
                .as("el problema declara type")
                .isNotBlank();
        assertThat(problem.path("status").asInt(-1)).isEqualTo(expected.value());
        assertThat(problem.path("title").asText(null)).isNotBlank();
        assertThat(problem.path("detail").asText(null)).isNotBlank();
        assertThat(problem.path("instance").asText(null)).isNotBlank();
        assertThat(problem.path("correlationId").asText(null)).isNotBlank();
        String header = response.getHeaders().getFirst(CORRELATION_HEADER);
        assertThat(header).as("toda respuesta lleva X-Correlation-Id").isNotBlank();
        assertThat(problem.path("correlationId").asText()).isEqualTo(header);
    }

    protected static JsonNode parseBody(ResponseEntity<String> response) {
        try {
            return JSON.readTree(response.getBody());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
