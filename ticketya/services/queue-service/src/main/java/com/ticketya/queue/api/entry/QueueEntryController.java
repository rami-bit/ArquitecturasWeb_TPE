package com.ticketya.queue.api.entry;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.ticketya.queue.api.error.Problem;
import com.ticketya.queue.api.identity.CurrentRequester;
import com.ticketya.queue.api.identity.Requester;
import com.ticketya.queue.application.QueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API externa de la fila virtual, publicada por el Gateway bajo /api/queue/**.
 * La identidad llega derivada en los headers del Gateway, nunca valida JWT.
 */
@RestController
@RequestMapping("/api/queue/events")
@Tag(name = "Virtual queue",
        description = "Waiting line of a high-demand event, served through the API Gateway")
public class QueueEntryController {

    private final QueueService service;

    public QueueEntryController(QueueService service) {
        this.service = service;
    }

    @PostMapping("/{eventId}/entries")
    @Operation(
            operationId = "joinEventQueue",
            summary = "Join the virtual queue of an event (idempotent per client)",
            responses = {
                    @ApiResponse(responseCode = "201", description = "A new entry was allocated",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = QueueEntryResponse.class))),
                    @ApiResponse(responseCode = "200", description = "The client already holds an active entry"),
                    @ApiResponse(responseCode = "404", description = "Event unknown or not published",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "409", description = "Event is not high demand",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "503", description = "Catalog unavailable",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class)))
            })
    public ResponseEntity<QueueEntryResponse> join(
            @Parameter(description = "Event identifier", required = true)
            @PathVariable("eventId") UUID eventId,
            @CurrentRequester Requester requester) {
        QueueService.JoinOutcome outcome = service.join(eventId, requester);
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(outcome.response());
    }

    @GetMapping("/{eventId}/entries/me")
    @Operation(
            operationId = "getMyQueueEntry",
            summary = "Poll the caller's own queue entry with promotion and expiry applied",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Current entry state",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = QueueEntryResponse.class))),
                    @ApiResponse(responseCode = "404", description = "The client has no active entry",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "409", description = "The event is no longer published",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class)))
            })
    public QueueEntryResponse poll(
            @Parameter(description = "Event identifier", required = true)
            @PathVariable("eventId") UUID eventId,
            @CurrentRequester Requester requester) {
        return service.poll(eventId, requester);
    }

    @DeleteMapping("/{eventId}/entries/me")
    @Operation(
            operationId = "leaveEventQueue",
            summary = "Abandon the caller's active queue entry",
            responses = {
                    @ApiResponse(responseCode = "204", description = "The entry is closed as LEFT"),
                    @ApiResponse(responseCode = "404", description = "The client has no active entry",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class)))
            })
    public ResponseEntity<Void> leave(
            @Parameter(description = "Event identifier", required = true)
            @PathVariable("eventId") UUID eventId,
            @CurrentRequester Requester requester) {
        service.leave(eventId, requester);
        return ResponseEntity.noContent().build();
    }
}
