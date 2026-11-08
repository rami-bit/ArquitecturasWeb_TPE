package com.ticketya.inventory.api.availability;

import com.ticketya.inventory.api.error.Problem;
import com.ticketya.inventory.application.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory/events")
@Tag(name = "Inventory availability",
        description = "Public availability query served through the API Gateway")
public class AvailabilityController {
    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    @GetMapping("/{eventId}/availability")
    @Operation(
            operationId = "getEventAvailability",
            summary = "Query the public availability of an event",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Event availability",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = AvailabilityResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Event or snapshot not found",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "503", description = "Catalog unavailable",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "500", description = "Unexpected error",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class)))
            })
    public AvailabilityResponse availability(
            @Parameter(description = "Event identifier", required = true)
            @PathVariable UUID eventId) {
        return service.availability(eventId);
    }
}
