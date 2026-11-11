package com.ticketya.payment.contract;

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
 * de Payment Mock) sin levantar Spring ni Docker: paths, operationIds, códigos
 * de respuesta, headers de correlación y schemas requeridos.
 */
class PaymentOpenApiDocumentContractTest {

    private static final Map<String, String> OPERATIONS = new LinkedHashMap<>();

    static {
        OPERATIONS.put("/internal/payments", "post");
        OPERATIONS.put("/internal/payments/by-order/{orderId}", "get");
        OPERATIONS.put("/internal/payments/{paymentId}", "get");
        OPERATIONS.put("/internal/payments/{paymentId}/refund", "post");
    }

    private static final Map<String, String> OPERATION_IDS = Map.of(
            "/internal/payments", "createPayment",
            "/internal/payments/by-order/{orderId}", "getPaymentByOrderId",
            "/internal/payments/{paymentId}", "getPaymentById",
            "/internal/payments/{paymentId}/refund", "refundPayment");

    private static final Map<String, Set<Integer>> MINIMUM_STATUS_CODES = Map.of(
            "/internal/payments", Set.of(201, 200, 400, 409),
            "/internal/payments/by-order/{orderId}", Set.of(200, 404),
            "/internal/payments/{paymentId}", Set.of(200, 404),
            "/internal/payments/{paymentId}/refund", Set.of(200, 404, 409));

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
        return (Map<String, Object>) operation(path).get("responses");
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
    void contractDeclaresTheExpectedPaymentOperationsWithTheirOperationIds() {
        for (Map.Entry<String, String> entry : OPERATIONS.entrySet()) {
            String path = entry.getKey();
            assertThat(paths().keySet())
                    .as("el contrato declara el path %s", path)
                    .contains(path);
            Map<String, Object> item = (Map<String, Object>) paths().get(path);
            assertThat(item.keySet())
                    .as("%s declara exactamente el método %s", path, entry.getValue())
                    .containsExactly(entry.getValue());
            assertThat(operation(path).get("operationId"))
                    .as("operationId de %s", path)
                    .isEqualTo(OPERATION_IDS.get(path));
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
                        .as("%s %s declara el header X-Correlation-Id", path, response.getKey())
                        .contains("X-Correlation-Id");
            }
        }
    }

    @Test
    void paymentSchemasDeclareTheContractRequiredFields() {
        assertThat(required("CreatePaymentRequest"))
                .contains("orderId", "amount", "currency", "method", "token");
        assertThat(required("PaymentResponse"))
                .contains("paymentId", "orderId", "status", "amount", "currency", "method",
                        "createdAt", "updatedAt");
        assertThat(required("Problem"))
                .contains("type", "title", "status", "detail", "instance", "correlationId");
    }

    @Test
    void simulationTokensAreExactlyTheDocumentedOnes() {
        Map<String, Object> token = (Map<String, Object>) properties("CreatePaymentRequest")
                .get("token");
        assertThat(token).as("CreatePaymentRequest.token debe declarar enum").isNotNull();
        assertThat(asStringList(token.get("enum")))
                .containsExactly("tok_approved", "tok_rejected", "tok_pending");
    }

    @Test
    void paymentResponseNeverExposesTheSimulationToken() {
        assertThat(properties("PaymentResponse").keySet())
                .as("PaymentResponse no expone token")
                .doesNotContain("token");
    }
}
