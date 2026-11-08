package com.ticketya.inventory.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paridad entre el OpenAPI de runtime (springdoc) y `docs/openapi.yaml`.
 * Este test es el que falla si cambian paths, códigos o esquemas sin actualizar la
 * implementación.
 */
@Testcontainers(disabledWithoutDocker = true)
class OpenApiRuntimeParityTest extends InventoryContractTestBase {

    /** Paths y métodos exigidos por el contrato versionado. */
    private static final Map<String, String> OPERATIONS = Map.ofEntries(
            Map.entry("/api/inventory/events/{eventId}/availability", "get"),
            Map.entry("/internal/reservations", "post"),
            Map.entry("/internal/reservations/by-order/{orderId}", "get"),
            Map.entry("/internal/reservations/{reservationId}", "get"),
            Map.entry("/internal/reservations/{reservationId}/confirm", "post"),
            Map.entry("/internal/reservations/{reservationId}/release", "post"),
            Map.entry("/internal/events/{eventId}/hold-ttl", "put"),
            Map.entry("/internal/events/{eventId}/snapshot", "get"));

    /**
     * Códigos mínimos exigidos en el runtime (2xx documentados por el contrato y
     * errores mínimos por operación).
     */
    private static final Map<String, Set<Integer>> RUNTIME_REQUIRED_CODES = Map.of(
            "/api/inventory/events/{eventId}/availability", Set.of(200, 404, 503),
            "/internal/reservations", Set.of(201, 200, 400, 404, 409, 503),
            "/internal/reservations/by-order/{orderId}", Set.of(200, 404),
            "/internal/reservations/{reservationId}", Set.of(200, 404),
            "/internal/reservations/{reservationId}/confirm", Set.of(200, 404, 409),
            "/internal/reservations/{reservationId}/release", Set.of(200, 404, 409),
            "/internal/events/{eventId}/hold-ttl", Set.of(200, 400, 404),
            "/internal/events/{eventId}/snapshot", Set.of(200, 404));

    private static final Set<String> INVENTORY_SCHEMAS = Set.of(
            "AvailabilityResponse", "SectorAvailability", "SeatAvailability",
            "ReservationRequest", "ReservationItemRequest", "ReservationResponse",
            "ReservationItem", "HoldTtlRequest", "EventHoldConfiguration");

    private static JsonNode runtimeDocument;

    private JsonNode runtime() throws Exception {
        if (runtimeDocument == null) {
            ResponseEntity<String> response = get("/v3/api-docs");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            runtimeDocument = JSON.readTree(response.getBody());
        }
        return runtimeDocument;
    }

    private static JsonNode contractDocument() {
        Path docs = locateOpenApiDocument();
        try (Reader reader = Files.newBufferedReader(docs, StandardCharsets.UTF_8)) {
            return JSON.valueToTree(new Yaml().load(reader));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path locateOpenApiDocument() {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("docs").resolve("openapi.yaml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException(
                "No se encontró docs/openapi.yaml subiendo desde " + System.getProperty("user.dir"));
    }

    // ------------------------------------------------------------------------ tests

    @Test
    void runtimeDocumentsEveryContractPath() throws Exception {
        JsonNode runtimePaths = runtime().path("paths");
        for (String path : OPERATIONS.keySet()) {
            assertThat(runtimePaths.has(path))
                    .as("el runtime documenta el path %s", path)
                    .isTrue();
        }
    }

    @Test
    void runtimeDocumentsTheContractSuccessAndErrorCodes() throws Exception {
        JsonNode runtimePaths = runtime().path("paths");
        for (Map.Entry<String, Set<Integer>> entry : RUNTIME_REQUIRED_CODES.entrySet()) {
            String path = entry.getKey();
            String method = OPERATIONS.get(path);
            JsonNode responses = runtimePaths.path(path).path(method).path("responses");
            assertThat(responses.isObject())
                    .as("el runtime documenta responses para %s %s", method, path)
                    .isTrue();
            Set<Integer> documented = new HashSet<>();
            responses.fieldNames().forEachRemaining(code -> {
                if (code.matches("\\d{3}")) {
                    documented.add(Integer.parseInt(code));
                }
            });
            assertThat(documented)
                    .as("códigos documentados en runtime para %s %s", method, path)
                    .containsAll(entry.getValue());
        }
    }

    @Test
    void runtimeProblemSchemaRequiresCorrelationId() throws Exception {
        JsonNode required = runtime().path("components").path("schemas")
                .path("Problem").path("required");
        assertThat(required.isArray()).as("Problem debe declarar required").isTrue();
        Set<String> fields = new HashSet<>();
        required.forEach(node -> fields.add(node.asText()));
        assertThat(fields).contains("correlationId", "status", "title", "detail", "instance");
    }

    @Test
    void runtimeInventorySchemasNeverExposePrice() throws Exception {
        JsonNode schemas = runtime().path("components").path("schemas");
        assertThat(schemas.isObject()).isTrue();
        for (String name : INVENTORY_SCHEMAS) {
            assertThat(schemas.has(name))
                    .as("el runtime documenta el schema %s", name)
                    .isTrue();
            JsonNode properties = schemas.path(name).path("properties");
            assertThat(properties.has("price"))
                    .as("%s no expone price en el runtime", name)
                    .isFalse();
        }
        schemas.fieldNames().forEachRemaining(name -> assertThat(
                        schemas.path(name).path("properties").has("price"))
                .as("ningún schema del runtime expone price (%s)", name)
                .isFalse());
    }

    @Test
    void contractAndRuntimeAgreeOnTheContractedOperations() throws Exception {
        JsonNode contractPaths = contractDocument().path("paths");
        JsonNode runtimePaths = runtime().path("paths");
        for (Map.Entry<String, String> entry : OPERATIONS.entrySet()) {
            String path = entry.getKey();
            String method = entry.getValue();
            assertThat(contractPaths.path(path).has(method))
                    .as("el contrato versionado declara %s %s", method, path)
                    .isTrue();
            assertThat(runtimePaths.path(path).has(method))
                    .as("el runtime declara %s %s", method, path)
                    .isTrue();
        }
    }
}
