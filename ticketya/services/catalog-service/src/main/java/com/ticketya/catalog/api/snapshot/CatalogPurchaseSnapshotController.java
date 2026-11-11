package com.ticketya.catalog.api.snapshot;

import com.ticketya.catalog.application.snapshot.CatalogPurchaseSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Contrato interno de lectura del snapshot publicado con el precio unitario de
 * cada sector, usado exclusivamente por Purchase Service. Es independiente del
 * snapshot de inventario {@code /snapshot}, que sigue sin precios.
 */
@RestController
@RequestMapping("/internal/events")
@Tag(name = "Catalog purchase snapshot (internal)",
        description = "Contrato interno de lectura del snapshot publicado con el precio de cada sector.")
public class CatalogPurchaseSnapshotController {

    private final CatalogPurchaseSnapshotService service;

    public CatalogPurchaseSnapshotController(CatalogPurchaseSnapshotService service) {
        this.service = service;
    }

    @GetMapping("/{eventId}/purchase-snapshot")
    @Operation(
            operationId = "getCatalogPurchaseSnapshot",
            summary = "Leer el snapshot de compra de un evento con precios",
            description = """
                    Devuelve el snapshot publicado e inmutable del evento con los datos
                    del lugar, los sectores, sus asientos y el precio unitario de cada
                    sector.

                    Es un contrato separado del snapshot de inventario
                    {@code /internal/events/{eventId}/snapshot}, que sigue sin precios y lo
                    consume Inventory. El Gateway no publica esta ruta; sólo es accesible
                    desde la red interna en catalog-service:8081.
                    """)
    @ApiResponse(responseCode = "200", description = "Snapshot publicado del evento con precios por sector.")
    @ApiResponse(responseCode = "404", description = "Evento inexistente, en borrador, suspendido o cancelado.")
    public CatalogPurchaseSnapshot getPurchaseSnapshot(
            @Parameter(description = "Identificador del evento.") @PathVariable UUID eventId) {
        return service.getPurchaseSnapshot(eventId);
    }
}
