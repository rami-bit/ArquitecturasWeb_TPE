package com.ticketya.catalog.contract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida el contrato versionado {@code docs/openapi.yaml} para el snapshot de
 * compra de Catalog sin levantar Spring: la ruta interna, sus códigos, el header de
 * correlación, el precio obligatorio por sector y la no regresión del snapshot de
 * inventario (que sigue sin precios).
 */
class CatalogPurchaseSnapshotDocumentContractTest {

    private static final String PURCHASE_SNAPSHOT_PATH =
            "/internal/events/{eventId}/purchase-snapshot";
    private static final String INVENTORY_SNAPSHOT_PATH =
            "/internal/events/{eventId}/snapshot";

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
        Map<String, Object> operation = (Map<String, Object>) item.get("get");
        assertThat(operation).as("GET %s debe existir", path).isNotNull();
        return operation;
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
    private static Map<String, Object> properties(String schemaName) {
        Object properties = schema(schemaName).get("properties");
        assertThat(properties).as("%s debe declarar properties", schemaName)
                .isInstanceOf(Map.class);
        return (Map<String, Object>) properties;
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(String schemaName) {
        Object required = schema(schemaName).get("required");
        assertThat(required).as("%s debe declarar required", schemaName).isInstanceOf(List.class);
        return (List<String>) required;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertThat(value).as("se esperaba un objeto en el contrato").isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static String refOf(Object node) {
        Object ref = asMap(node).get("$ref");
        assertThat(ref).as("se esperaba una referencia $ref en el contrato").isNotNull();
        return ref.toString();
    }

    // ------------------------------------------------------------------------ tests

    @Test
    void contractDeclaresThePurchaseSnapshotOperationWithCorrelationAndErrors() {
        Map<String, Object> operation = operation(PURCHASE_SNAPSHOT_PATH);
        assertThat(operation.get("operationId")).isEqualTo("getCatalogPurchaseSnapshot");

        List<Object> parameters = (List<Object>) operation.get("parameters");
        assertThat(parameters).as("%s declara X-Correlation-Id", PURCHASE_SNAPSHOT_PATH)
                .isNotNull();
        assertThat(parameters.stream().anyMatch(parameter ->
                        parameter instanceof Map<?, ?> map
                                && "#/components/parameters/CorrelationId".equals(map.get("$ref"))))
                .as("%s declara el parámetro X-Correlation-Id", PURCHASE_SNAPSHOT_PATH)
                .isTrue();

        Map<String, Object> responses = responses(PURCHASE_SNAPSHOT_PATH);
        assertThat(responses.keySet()).contains("200", "404");

        Map<String, Object> ok = asMap(responses.get("200"));
        Map<String, Object> okContent = asMap(ok.get("content"));
        assertThat(refOf(asMap(okContent.get("application/json")).get("schema")))
                .as("el 200 responde CatalogPurchaseSnapshot")
                .isEqualTo("#/components/schemas/CatalogPurchaseSnapshot");
        assertThat(asMap(ok.get("headers")).keySet()).contains("X-Correlation-Id");

        Map<String, Object> notFound = asMap(responses.get("404"));
        Map<String, Object> notFoundContent = asMap(notFound.get("content"));
        assertThat(notFoundContent.keySet())
                .as("el 404 usa application/problem+json")
                .contains("application/problem+json");
        assertThat(asMap(notFound.get("headers")).keySet()).contains("X-Correlation-Id");
    }

    @Test
    void purchaseSnapshotSectorsRequireTheUnitPrice() {
        Map<String, Object> sectors =
                asMap(properties("CatalogPurchaseSnapshot").get("sectors"));
        assertThat(refOf(sectors.get("items")))
                .as("los sectores del purchase-snapshot usan PurchaseSnapshotSector")
                .isEqualTo("#/components/schemas/PurchaseSnapshotSector");

        Map<String, Object> sectorSchema = schema("PurchaseSnapshotSector");
        assertThat(sectorSchema.get("additionalProperties")).isEqualTo(false);
        assertThat(required("PurchaseSnapshotSector"))
                .contains("sourceSectorId", "name", "type", "capacity", "price", "seats");

        Map<String, Object> price =
                asMap(properties("PurchaseSnapshotSector").get("price"));
        assertThat(price.get("type")).isEqualTo("number");
        assertThat(price.get("minimum")).isEqualTo(0.01);
    }

    @Test
    void inventorySnapshotOperationAndSchemaKeepExcludingPrices() {
        // No regresión documental: el contrato sin precios sigue declarado para Inventory.
        Map<String, Object> inventoryResponses = responses(INVENTORY_SNAPSHOT_PATH);
        assertThat(inventoryResponses.keySet()).contains("200", "404");
        Map<String, Object> ok = asMap(inventoryResponses.get("200"));
        Map<String, Object> okContent = asMap(ok.get("content"));
        assertThat(refOf(asMap(okContent.get("application/json")).get("schema")))
                .as("el 200 del snapshot de inventario responde CatalogEventSnapshot")
                .isEqualTo("#/components/schemas/CatalogEventSnapshot");

        Map<String, Object> sectorSchema = schema("SnapshotSector");
        assertThat(sectorSchema.get("additionalProperties")).isEqualTo(false);
        assertThat(properties("SnapshotSector").keySet())
                .as("el sector del snapshot de inventario sigue sin price")
                .doesNotContain("price", "currency");
        assertThat(properties("CatalogEventSnapshot").keySet())
                .as("el snapshot de inventario sigue sin campos económicos")
                .doesNotContain("currency", "totalPrice", "minimumPrice");
        assertThat(required("CatalogEventSnapshot"))
                .as("ambos snapshots comparten los mismos campos obligatorios")
                .containsExactlyInAnyOrderElementsOf(required("CatalogPurchaseSnapshot"));
    }
}
