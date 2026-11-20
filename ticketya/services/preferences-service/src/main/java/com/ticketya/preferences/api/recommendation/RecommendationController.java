package com.ticketya.preferences.api.recommendation;

import com.ticketya.preferences.api.identity.CurrentRequester;
import com.ticketya.preferences.api.identity.Requester;
import com.ticketya.preferences.application.recommendation.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Recomendaciones explicables para el cliente autenticado. */
@RestController
@RequestMapping("/api/customers/me/recommendations")
@Tag(name = "Event recommendations",
        description = "Recomendaciones de eventos publicados y futuros (solo rol CLIENT).")
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            operationId = "getCustomerRecommendations",
            summary = "Recomendar eventos publicados y futuros afines al cliente",
            description = """
                    Calcula recomendaciones en cada consulta. No informa disponibilidad
                    ni reserva inventario. Si Purchase no responde degrada con
                    `HISTORY_UNAVAILABLE`; si Catalog no responde responde `503`.
                    """)
    @ApiResponse(responseCode = "200", description = "Recomendaciones del cliente.")
    @ApiResponse(responseCode = "400", description = "Limite fuera de rango.")
    @ApiResponse(responseCode = "401", description = "Token ausente o inválido.")
    @ApiResponse(responseCode = "403", description = "Rol distinto de CLIENT.")
    @ApiResponse(responseCode = "503", description = "Catalog no disponible.")
    public ResponseEntity<EventRecommendationPage> recommend(
            @CurrentRequester Requester requester,
            @Parameter(description = "Cantidad máxima de recomendaciones (1 a 20).")
            @RequestParam(name = "limit", defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(service.recommend(requester.id(), limit));
    }
}
