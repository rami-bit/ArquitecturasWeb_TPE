package com.ticketya.catalog.api.snapshot;

import com.ticketya.catalog.application.snapshot.CatalogSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/events")
@Tag(name = "Catalog snapshot (internal)",
        description = "Contrato interno de lectura del snapshot publicado, sin precios.")
public class CatalogSnapshotController {

    private final CatalogSnapshotService service;

    public CatalogSnapshotController(CatalogSnapshotService service) {
        this.service = service;
    }

    @GetMapping("/{eventId}/snapshot")
    @Operation(
            operationId = "getCatalogEventSnapshot",
            summary = "Leer el snapshot publicado de un evento",
            description = """
                    Devuelve el snapshot inmutable con los identificadores de origen de
                    sectores y asientos, sus tipos, capacidades y datos de ubicación.
                    La respuesta no incluye precios.
                    """)
    @ApiResponse(responseCode = "200", description = "Snapshot publicado del evento.")
    @ApiResponse(responseCode = "404", description = "Evento inexistente, en borrador o sin snapshot.")
    @ApiResponse(responseCode = "500", description = "Error inesperado.")
    public CatalogEventSnapshot getSnapshot(
            @Parameter(description = "Identificador del evento.") @PathVariable UUID eventId) {
        return service.getSnapshot(eventId);
    }
}
