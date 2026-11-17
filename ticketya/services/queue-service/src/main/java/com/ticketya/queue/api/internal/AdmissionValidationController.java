package com.ticketya.queue.api.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.ticketya.queue.api.error.Problem;
import com.ticketya.queue.application.QueueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API interna (NO publicada por el Gateway): valida el token de admisión de un
 * turno y lo consume en la misma transacción.
 */
@RestController
@RequestMapping("/internal/admissions")
@Tag(name = "Internal admissions",
        description = "Token validation for admitted entries, called by other services")
public class AdmissionValidationController {

    private final QueueService service;

    public AdmissionValidationController(QueueService service) {
        this.service = service;
    }

    @PostMapping("/validate")
    @Operation(
            operationId = "validateAdmission",
            summary = "Validate an admission token and consume the entry",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Token accepted; entry is now CONSUMED",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = AdmissionValidationResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Token mismatched, expired or entry not admissible",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "404", description = "No entry for this event and client",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = Problem.class)))
            })
    public AdmissionValidationResponse validate(@Valid @RequestBody AdmissionValidationRequest request) {
        return service.validate(request);
    }
}
