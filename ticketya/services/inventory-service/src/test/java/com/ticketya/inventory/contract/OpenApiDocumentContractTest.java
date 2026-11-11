package com.ticketya.inventory.contract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida el contrato versionado {@code docs/openapi.yaml} sin levantar Spring ni Docker.
 * Esta suite define la aceptación del documento: paths, métodos, códigos, headers de
 * correlación, esquemas de problema y ausencia de precios en los schemas de Inventory.
 */
class OpenApiDocumentContractTest {

    private static final Map<String, String> OPERATIONS = new LinkedHashMap<>();

    static {
        OPERATIONS.put("/api/inventory/events/{eventId}/availability", "get");
        OPERATIONS.put("/internal/reservations", "post");
        OPERATIONS.put("/internal/reservations/by-order/{orderId}", "get");
        OPERATIONS.put("/internal/reservations/{reservationId}", "get");
        OPERATIONS.put("/internal/reservations/{reservationId}/confirm", "post");
        OPERATIONS.put("/internal/reservations/{reservationId}/release", "post");
        OPERATIONS.put("/internal/events/{eventId}/hold-ttl", "put");
        OPERATIONS.put("/internal/events/{eventId}/snapshot", "get");
    }

    private static final Map<String, Set<Integer>> MINIMUM_STATUS_CODES = Map.of(
            "/api/inventory/events/{eventId}/availability", Set.of(200, 404, 503, 504),
            "/internal/reservations", Set.of(201, 200, 400, 404, 409, 503),
            "/internal/reservations/by-order/{orderId}", Set.of(200, 404),
            "/internal/reservations/{reservationId}", Set.of(200, 404),
            "/internal/reservations/{reservationId}/confirm", Set.of(200, 404, 409),
            "/internal/reservations/{reservationId}/release", Set.of(200, 404, 409),
            "/internal/events/{eventId}/hold-ttl", Set.of(200, 400, 404),
            "/internal/events/{eventId}/snapshot", Set.of(200, 404));

    private static final List<String> INVENTORY_SCHEMAS_WITHOUT_PRICE = List.of(
            "AvailabilityResponse", "SectorAvailability", "SeatAvailability",
            "ReservationRequest", "ReservationItemRequest", "ReservationResponse",
            "ReservationItem", "HoldTtlRequest", "EventHoldConfiguration");

    private static Map<String, Object> contract;

    @BeforeAll
    static void loadContract() {
        Path docs = locateOpenApiDocument();
        try (Reader reader = Files.newBufferedReader(docs, StandardCharsets.UTF_8)) {
            contract = new Yaml().load(reader);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        assertThat(contract).as("docs/openapi.yaml debe ser un documento OpenAPI").isNotNull();
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

    // ------------------------------------------------------------------ acceso al doc

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paths() {
        return (Map<String, Object>) contract.get("paths");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> operation(String path) {
        Map<String, Object> item = (Map<String, Object>) paths().get(path);
        assertThat(item).as("el path %s debe existir en el contrato", path).isNotNull();
        Map<String, Object> op = (Map<String, Object>) item.get(OPERATIONS.get(path));
        assertThat(op)
                .as("la operación %s de %s debe existir", OPERATIONS.get(path), path)
                .isNotNull();
        return op;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> responses(String path) {
        Map<String, Object> op = operation(path);
        return (Map<String, Object>) op.get("responses");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schema(String name) {
        Map<String, Object> components = (Map<String, Object>) contract.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> schema = (Map<String, Object>) schemas.get(name);
        assertThat(schema).as("el schema %s debe existir", name).isNotNull();
        return schema;
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(String schemaName) {
        Object required = schema(schemaName).get("required");
        assertThat(required).as("%s debe declarar required", schemaName).isInstanceOf(List.class);
        return (List<String>) required;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(String schemaName) {
        Object properties = schema(schemaName).get("properties");
        assertThat(properties).as("%s debe declarar properties", schemaName)
                .isInstanceOf(Map.class);
        return (Map<String, Object>) properties;
    }

    // ------------------------------------------------------------------------ tests

    @Test
    void contractDeclaresExactlyTheExpectedPathsAndMethods() {
        Set<String> expectedPaths = OPERATIONS.keySet();

        // docs/openapi.yaml es el contrato consolidado: además de los 8 paths de
        // Inventory declara paths de Catalog, Purchase y Payment Mock. Por eso no se
        // puede exigir que contenga SOLO los paths de Inventory; se verifica que los
        // 8 paths de Inventory estén presentes y completos (un único método cada uno),
        // sin prohibir los paths de los demás servicios.
        assertThat(paths().keySet())
                .as("el contrato versionado declara los 8 paths de Inventory")
                .containsAll(expectedPaths);

        for (String path : expectedPaths) {
            Map<String, Object> item = (Map<String, Object>) paths().get(path);
            assertThat(item.keySet())
                    .as("%s declara exactamente el método %s", path, OPERATIONS.get(path))
                    .containsExactly(OPERATIONS.get(path));
        }
    }

    @Test
    void everyOperationDeclaresTheMinimumStatusCodes() {
        for (Map.Entry<String, Set<Integer>> entry : MINIMUM_STATUS_CODES.entrySet()) {
            Set<Integer> documented = responses(entry.getKey()).keySet().stream()
                    .filter(code -> code.matches("\\d{3}"))
                    .map(Integer::parseInt)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(documented)
                    .as("códigos mínimos de %s", entry.getKey())
                    .containsAll(entry.getValue());
        }
    }

    @Test
    void everyErrorResponseDeclaresProblemJson() {
        for (String path : OPERATIONS.keySet()) {
            for (Map.Entry<String, Object> response : responses(path).entrySet()) {
                String code = response.getKey();
                if (!code.matches("\\d{3}") || Integer.parseInt(code) < 400) {
                    continue;
                }
                Map<String, Object> body = (Map<String, Object>) response.getValue();
                Map<String, Object> content = (Map<String, Object>) body.get("content");
                assertThat(content)
                        .as("%s %s declara content", path, code)
                        .isNotNull();
                assertThat(content.keySet())
                        .as("%s %s declara application/problem+json", path, code)
                        .contains("application/problem+json");
            }
        }
    }

    @Test
    void everyOperationDeclaresCorrelationIdRequestParameterAndResponseHeaders() {
        for (String path : OPERATIONS.keySet()) {
            Map<String, Object> op = operation(path);
            List<Object> parameters = (List<Object>) op.get("parameters");
            assertThat(parameters)
                    .as("%s declara el parámetro X-Correlation-Id", path)
                    .isNotNull();
            boolean declaresCorrelationParameter = parameters.stream().anyMatch(parameter -> {
                if (!(parameter instanceof Map<?, ?> map)) {
                    return false;
                }
                Object ref = map.get("$ref");
                if ("#/components/parameters/CorrelationId".equals(ref)) {
                    return true;
                }
                return "X-Correlation-Id".equals(map.get("name"));
            });
            assertThat(declaresCorrelationParameter)
                    .as("%s declara el parámetro X-Correlation-Id", path)
                    .isTrue();

            for (Map.Entry<String, Object> response : responses(path).entrySet()) {
                Map<String, Object> body = (Map<String, Object>) response.getValue();
                Map<String, Object> headers = (Map<String, Object>) body.get("headers");
                assertThat(headers)
                        .as("%s %s declara headers", path, response.getKey())
                        .isNotNull();
                assertThat(headers.keySet())
                        .as("%s %s declara el header X-Correlation-Id",
                                path, response.getKey())
                        .contains("X-Correlation-Id");
            }
        }
    }

    @Test
    void problemSchemaRequiresTheContractFields() {
        assertThat(required("Problem"))
                .contains("status", "title", "detail", "instance", "correlationId");
    }

    @Test
    void inventorySchemasNeverExposePrice() {
        for (String name : INVENTORY_SCHEMAS_WITHOUT_PRICE) {
            assertThat(properties(name).keySet())
                    .as("%s no expone price", name)
                    .doesNotContain("price");
        }
    }

    @Test
    void availabilitySchemasNeverExposeOrderOrReservationIdentifiers() {
        for (String name : List.of(
                "AvailabilityResponse", "SectorAvailability", "SeatAvailability")) {
            assertThat(properties(name).keySet())
                    .as("%s no expone orderId ni reservationId", name)
                    .doesNotContain("orderId", "reservationId");
        }
    }

    @Test
    void holdTtlRequestBoundsTtlSecondsBetween60And3600() {
        Map<String, Object> ttlSeconds = (Map<String, Object>)
                properties("HoldTtlRequest").get("ttlSeconds");
        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds.get("minimum")).isEqualTo(60);
        assertThat(ttlSeconds.get("maximum")).isEqualTo(3600);
        assertThat(required("HoldTtlRequest")).contains("ttlSeconds");
    }

    @Test
    void requiredKeysMatchTheContract() {
        assertThat(required("AvailabilityResponse"))
                .contains("eventId", "generatedAt", "sectors");
        assertThat(required("ReservationResponse"))
                .contains("reservationId", "orderId", "eventId", "status", "createdAt", "items");
        assertThat(required("ReservationRequest"))
                .contains("orderId", "eventId", "items");
        assertThat(required("EventHoldConfiguration"))
                .contains("eventId", "holdTtlSeconds");
    }
}
