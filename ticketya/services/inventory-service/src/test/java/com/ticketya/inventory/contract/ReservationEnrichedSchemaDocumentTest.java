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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida en {@code docs/openapi.yaml} el schema `ReservationItem` de la respuesta
 * enriquecida: los seis campos nuevos además de `sectorId`, `seatId` y `quantity`,
 * la semántica nullable de los campos de asiento para admisión general y el
 * `additionalProperties: false` que impide colar precios o datos del comprador.
 */
class ReservationEnrichedSchemaDocumentTest {

    private static final Set<String> CONTRACTED_ITEM_FIELDS = Set.of(
            "sectorId", "seatId", "quantity", "sourceSectorId", "sourceSeatId",
            "sectorName", "sectorType", "rowLabel", "seatNumber");

    /** Campos que sólo pueden ser null en sectores GENERAL_ADMISSION. */
    private static final Set<String> NULLABLE_SEAT_FIELDS = Set.of(
            "seatId", "sourceSeatId", "rowLabel", "seatNumber");

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

    // ------------------------------------------------------------------------ tests

    @Test
    void reservationItemRequiresEveryContractedField() {
        Object required = schema("ReservationItem").get("required");
        assertThat(required).as("ReservationItem debe declarar required")
                .isInstanceOf(List.class);

        Set<String> requiredFields = new HashSet<>();
        ((List<?>) required).forEach(field -> requiredFields.add(field.toString()));

        assertThat(requiredFields)
                .as("ReservationItem exige los campos conservados y los enriquecidos")
                .containsExactlyInAnyOrderElementsOf(CONTRACTED_ITEM_FIELDS);
    }

    @Test
    void reservationItemDeclaresTheContractedPropertiesWithTheirSemantics() {
        Map<String, Object> properties = properties("ReservationItem");
        assertThat(properties.keySet())
                .as("ReservationItem sólo declara los campos del contrato")
                .containsExactlyInAnyOrderElementsOf(CONTRACTED_ITEM_FIELDS);
        assertThat(schema("ReservationItem").get("additionalProperties"))
                .as("ReservationItem no admite propiedades extra (sin precios ni comprador)")
                .isEqualTo(false);

        for (String field : NULLABLE_SEAT_FIELDS) {
            Map<String, Object> property = asMap(properties.get(field));
            assertThat(property.get("nullable"))
                    .as("%s es nullable para GENERAL_ADMISSION", field)
                    .isEqualTo(true);
        }
        for (String field : CONTRACTED_ITEM_FIELDS) {
            if (NULLABLE_SEAT_FIELDS.contains(field)) {
                continue;
            }
            assertThat(asMap(properties.get(field)).get("nullable"))
                    .as("%s es obligatorio y no nullable", field)
                    .isNull();
        }

        assertThat(asMap(properties.get("quantity")).get("minimum")).isEqualTo(1);
        assertThat(asMap(properties.get("sectorType")).get("$ref"))
                .as("sectorType reutiliza el enum SectorType")
                .isEqualTo("#/components/schemas/SectorType");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        assertThat(value).as("se esperaba un objeto en el contrato").isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
