package com.ticketya.purchase.contract;

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
 * Valida el contrato versionado {@code docs/openapi.yaml} (sólo las operaciones
 * de Purchase) sin levantar Spring ni Docker: paths, métodos, operationIds,
 * códigos de respuesta, headers de correlación y schemas requeridos.
 */
class PurchaseOpenApiDocumentContractTest {

    private record Operation(String path, String method, String operationId) {
    }

    private static final List<Operation> OPERATIONS = List.of(
            new Operation("/api/orders", "post", "createOrder"),
            new Operation("/api/orders", "get", "listOrders"),
            new Operation("/api/orders/{orderId}", "get", "getOrder"),
            new Operation("/api/orders/{orderId}/reconcile", "post", "reconcileOrder"));

    private static final Map<String, Set<Integer>> MINIMUM_STATUS_CODES = new LinkedHashMap<>();

    static {
        MINIMUM_STATUS_CODES.put("/api/orders:post",
                Set.of(201, 200, 202, 402, 400, 403, 404, 409, 503));
        MINIMUM_STATUS_CODES.put("/api/orders:get", Set.of(200, 400, 403));
        MINIMUM_STATUS_CODES.put("/api/orders/{orderId}:get", Set.of(200, 403, 404));
        MINIMUM_STATUS_CODES.put("/api/orders/{orderId}/reconcile:post",
                Set.of(200, 403, 404, 409));
    }

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
    private static Map<String, Object> operation(Operation operation) {
        Map<String, Object> item = (Map<String, Object>) paths().get(operation.path());
        assertThat(item).as("el path %s debe existir en el contrato", operation.path()).isNotNull();
        Map<String, Object> op = (Map<String, Object>) item.get(operation.method());
        assertThat(op)
                .as("la operación %s de %s debe existir", operation.method(), operation.path())
                .isNotNull();
        return op;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> responses(Operation operation) {
        return (Map<String, Object>) operation(operation).get("responses");
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

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        assertThat(value).as("se espera una lista de strings").isInstanceOf(List.class);
        return (List<String>) value;
    }

    // ------------------------------------------------------------------------ tests

    @Test
    void contractDeclaresTheExpectedOrderOperationsWithTheirOperationIds() {
        Set<String> expectedPaths = OPERATIONS.stream()
                .map(Operation::path)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(paths().keySet())
                .as("el contrato declara los paths de órdenes")
                .containsAll(expectedPaths);

        for (Operation operation : OPERATIONS) {
            assertThat(operation(operation).get("operationId"))
                    .as("operationId de %s %s", operation.method(), operation.path())
                    .isEqualTo(operation.operationId());
        }

        // `POST /api/orders` y `GET /api/orders` comparten el mismo path.
        Map<String, Object> ordersItem =
                (Map<String, Object>) paths().get("/api/orders");
        assertThat(ordersItem.keySet())
                .as("/api/orders declara post y get")
                .containsExactlyInAnyOrder("post", "get");
    }

    @Test
    void everyOperationDeclaresTheMinimumStatusCodes() {
        for (Map.Entry<String, Set<Integer>> entry : MINIMUM_STATUS_CODES.entrySet()) {
            String[] parts = entry.getKey().split(":");
            Operation operation = OPERATIONS.stream()
                    .filter(candidate -> candidate.path().equals(parts[0])
                            && candidate.method().equals(parts[1]))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("operación no encontrada: "
                            + entry.getKey()));
            Set<Integer> documented = responses(operation).keySet().stream()
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
        for (Operation operation : OPERATIONS) {
            for (Map.Entry<String, Object> response : responses(operation).entrySet()) {
                String code = response.getKey();
                if (!code.matches("\\d{3}") || Integer.parseInt(code) < 400) {
                    continue;
                }
                Map<String, Object> body = (Map<String, Object>) response.getValue();
                Map<String, Object> content = (Map<String, Object>) body.get("content");
                assertThat(content)
                        .as("%s %s %s declara content", operation.method(), operation.path(), code)
                        .isNotNull();
                assertThat(content.keySet())
                        .as("%s %s %s declara application/problem+json",
                                operation.method(), operation.path(), code)
                        .contains("application/problem+json");
            }
        }
    }

    @Test
    void everyOperationDeclaresCorrelationIdRequestParameterAndResponseHeaders() {
        for (Operation operation : OPERATIONS) {
            Map<String, Object> op = operation(operation);
            List<Object> parameters = (List<Object>) op.get("parameters");
            assertThat(parameters)
                    .as("%s %s declara el parámetro X-Correlation-Id",
                            operation.method(), operation.path())
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
                    .as("%s %s declara el parámetro X-Correlation-Id",
                            operation.method(), operation.path())
                    .isTrue();

            for (Map.Entry<String, Object> response : responses(operation).entrySet()) {
                Map<String, Object> body = (Map<String, Object>) response.getValue();
                Map<String, Object> headers = (Map<String, Object>) body.get("headers");
                assertThat(headers)
                        .as("%s %s %s declara headers",
                                operation.method(), operation.path(), response.getKey())
                        .isNotNull();
                assertThat(headers.keySet())
                        .as("%s %s %s declara el header X-Correlation-Id",
                                operation.method(), operation.path(), response.getKey())
                        .contains("X-Correlation-Id");
            }
        }
    }

    @Test
    void orderSchemasDeclareTheContractRequiredFields() {
        assertThat(required("CreateOrderRequest"))
                .contains("eventId", "items", "payment");
        assertThat(required("CreateOrderItem"))
                .contains("sectorId");
        assertThat(required("CreateOrderPayment"))
                .contains("method", "token");
        assertThat(required("OrderResponse"))
                .contains("orderId", "status", "customerId", "eventId", "eventName",
                        "eventStartsAt", "venueName", "venueCity", "currency", "totalAmount",
                        "reservationExpiresAt", "createdAt", "updatedAt", "payment", "items");
        assertThat(required("OrderPage"))
                .contains("items", "page", "size", "totalElements", "totalPages");
        assertThat(required("Problem"))
                .contains("type", "title", "status", "detail", "instance", "correlationId");
    }

    @Test
    void orderRequestNeverCarriesPricesOrAmounts() {
        assertThat(properties("CreateOrderRequest").keySet())
                .as("CreateOrderRequest no expone precios")
                .doesNotContain("price", "unitPrice", "totalAmount", "amount");
        assertThat(properties("CreateOrderItem").keySet())
                .as("CreateOrderItem no expone precios")
                .doesNotContain("price", "unitPrice", "subtotal", "amount");
        assertThat(properties("CreateOrderPayment").keySet())
                .as("CreateOrderPayment no expone montos")
                .doesNotContain("amount", "totalAmount");
    }

    @Test
    void simulationTokensAreExactlyTheDocumentedOnes() {
        Map<String, Object> token = (Map<String, Object>) properties("CreateOrderPayment")
                .get("token");
        assertThat(token).as("CreateOrderPayment.token debe declarar enum").isNotNull();
        assertThat(asStringList(token.get("enum")))
                .containsExactly("tok_approved", "tok_rejected", "tok_pending");
    }
}
