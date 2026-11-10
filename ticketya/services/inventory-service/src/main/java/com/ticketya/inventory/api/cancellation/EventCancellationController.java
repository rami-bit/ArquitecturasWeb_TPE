package com.ticketya.inventory.api.cancellation;

import com.ticketya.inventory.application.EventCancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/events")
@Tag(name = "Event cancellation (internal)",
        description = "Blocks new sales of a cancelled event")
public class EventCancellationController {
    private final EventCancellationService service;

    public EventCancellationController(EventCancellationService service) {
        this.service = service;
    }

    @PutMapping("/{eventId}/cancellation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "cancelEventInventory",
            summary = "Mark the inventory of an event as cancelled",
            responses = @ApiResponse(responseCode = "204", description = "Inventory blocked (idempotent)"))
    public void cancel(@Parameter(description = "Event identifier", required = true)
                       @PathVariable UUID eventId) {
        service.cancel(eventId);
    }
}
