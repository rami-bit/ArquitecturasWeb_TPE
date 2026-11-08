package com.ticketya.inventory.api.configuration;

import com.ticketya.inventory.api.error.Problem;
import com.ticketya.inventory.application.HoldTtlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/events")
@Tag(name = "Inventory configuration (internal)",
        description = "Internal per-event hold configuration")
public class HoldTtlController {
    private final HoldTtlService service;

    public HoldTtlController(HoldTtlService service) {
        this.service = service;
    }

    @PutMapping("/{eventId}/hold-ttl")
    @Operation(
            operationId = "updateEventHoldTtl",
            summary = "Configure the hold TTL of an event",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Configured hold TTL",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = EventHoldConfiguration.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid TTL",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "404", description = "Inventory not materialized",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class)))
            })
    public EventHoldConfiguration update(
            @Parameter(description = "Event identifier", required = true)
            @PathVariable UUID eventId,
            @Valid @RequestBody HoldTtlRequest request) {
        return service.update(eventId, request.ttlSeconds());
    }
}
