package com.ticketya.catalog.api.recommendation;

import com.ticketya.catalog.application.recommendation.RecommendationCandidateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ticketya.catalog.api.error.ApiProblemException;

/** Contrato interno de candidatos para Preferences Service. */
@RestController
@RequestMapping("/internal/events")
@Tag(name = "Recommendation candidates (internal)",
        description = "Eventos publicados y futuros para recomendaciones.")
public class RecommendationCandidateController {

    private final RecommendationCandidateService service;

    public RecommendationCandidateController(RecommendationCandidateService service) {
        this.service = service;
    }

    @GetMapping("/recommendation-candidates")
    @Operation(
            operationId = "getRecommendationCandidates",
            summary = "Candidatos publicados y futuros para recomendar",
            description = """
                    Sólo eventos PUBLISHED con fecha futura, ordenados por fecha y
                    identificador, con tope de 200. No informa disponibilidad.
                    """)
    @ApiResponse(responseCode = "200", description = "Candidatos disponibles.")
    @ApiResponse(responseCode = "400", description = "Limite fuera de rango.")
    public EventCandidatePageResponse candidates(
            @Parameter(description = "Cantidad máxima de candidatos (1 a 200).")
            @RequestParam(name = "limit", defaultValue = "200") Integer limit) {
        if (limit == null || limit < 1 || limit > RecommendationCandidateService.MAX_LIMIT) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, "Invalid limit",
                    "limit must be between 1 and " + RecommendationCandidateService.MAX_LIMIT);
        }
        return service.candidates(limit);
    }
}
