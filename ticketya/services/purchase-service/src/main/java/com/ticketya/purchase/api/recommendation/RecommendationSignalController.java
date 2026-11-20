package com.ticketya.purchase.api.recommendation;

import com.ticketya.purchase.application.recommendation.RecommendationSignalsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Contrato interno de señales de compra para Preferences Service. */
@RestController
@RequestMapping("/internal/customers")
@Tag(name = "Recommendation signals (internal)",
        description = "Señales agregadas de historial de compras para recomendaciones.")
public class RecommendationSignalController {

    private final RecommendationSignalsService service;

    public RecommendationSignalController(RecommendationSignalsService service) {
        this.service = service;
    }

    @GetMapping("/{customerId}/recommendation-signals")
    @Operation(
            operationId = "getRecommendationSignals",
            summary = "Señales de historial de compras de un cliente",
            description = """
                    Sólo lectura sobre purchase_orders: eventos comprados y co-compras
                    acotadas a 200. Un cliente sin compras responde 200 con listas vacías.
                    """)
    @ApiResponse(responseCode = "200", description = "Señales del cliente.")
    public RecommendationSignalsResponse signals(@PathVariable("customerId") UUID customerId) {
        return service.signals(customerId);
    }
}
