package com.ticketya.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documents the Catalog-owned internal snapshot contract in Inventory's runtime
 * OpenAPI so both services stay visible from the same versioned contract. The path
 * is documentation-only: Inventory never serves it.
 */
@Configuration
public class OpenApiInventoryCustomizerConfiguration {

    private static final String SNAPSHOT_PATH = "/internal/events/{eventId}/snapshot";
    private static final String PROBLEM_REF = "#/components/schemas/Problem";

    @Bean
    public OpenApiCustomizer catalogSnapshotContractCustomizer() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths == null) {
                paths = new Paths();
                openApi.setPaths(paths);
            }
            if (paths.containsKey(SNAPSHOT_PATH)) {
                return;
            }

            Operation operation = new Operation()
                    .operationId("getCatalogEventSnapshot")
                    .summary("Leer el snapshot publicado de un evento")
                    .description("""
                            Contrato interno de Catalog que Inventory consume para
                            materializar el inventario de un evento. No incluye precios
                            y no se publica por el Gateway.
                            """)
                    .addTagsItem("Catalog snapshot (internal)")
                    .addParametersItem(new Parameter()
                            .name("eventId")
                            .in("path")
                            .required(true)
                            .schema(new StringSchema().format("uuid")))
                    .addParametersItem(new Parameter()
                            .name("X-Correlation-Id")
                            .in("header")
                            .required(false)
                            .schema(new StringSchema().minLength(1)));

            ApiResponses responses = new ApiResponses();
            responses.addApiResponse("200", new ApiResponse()
                    .description("Snapshot publicado del evento sin precios.")
                    .content(new Content().addMediaType(
                            "application/json",
                            new MediaType().schema(new Schema<>().type("object")))));
            responses.addApiResponse("404", problemResponse(
                    openApi,
                    "El evento no existe, está en borrador o no tiene snapshot publicado."));
            responses.addApiResponse("500", problemResponse(
                    openApi,
                    "Error inesperado en Catalog."));
            operation.setResponses(responses);

            paths.addPathItem(SNAPSHOT_PATH, new PathItem().get(operation));
        };
    }

    private static ApiResponse problemResponse(OpenAPI openApi, String description) {
        ApiResponse response = new ApiResponse().description(description);
        if (hasProblemSchema(openApi)) {
            response.setContent(new Content().addMediaType(
                    "application/problem+json",
                    new MediaType().schema(new Schema<>().$ref(PROBLEM_REF))));
        }
        return response;
    }

    private static boolean hasProblemSchema(OpenAPI openApi) {
        Components components = openApi.getComponents();
        return components != null
                && components.getSchemas() != null
                && components.getSchemas().containsKey("Problem");
    }
}
