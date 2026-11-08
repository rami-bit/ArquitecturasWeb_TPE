package com.ticketya.inventory.api.reservation;

import com.ticketya.inventory.api.error.Problem;
import com.ticketya.inventory.application.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/reservations")
@Tag(name = "Inventory reservations (internal)",
        description = "Internal reservation commands consumed by Purchase Service")
public class ReservationController {
    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            operationId = "createReservation",
            summary = "Create or recover a temporary reservation",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Reservation created",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ReservationResponse.class))),
                    @ApiResponse(responseCode = "200", description = "Idempotent replay",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ReservationResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "404", description = "Event not found",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "409", description = "Availability or idempotency conflict",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "503", description = "Catalog unavailable",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class)))
            })
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody ReservationRequest request) {
        ReservationService.ReservationResult result = service.create(request);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.reservation());
    }

    @GetMapping("/by-order/{orderId}")
    @Operation(
            operationId = "getReservationByOrderId",
            summary = "Recover a reservation by order identifier",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reservation found",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ReservationResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Reservation not found",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class)))
            })
    public ReservationResponse byOrder(
            @Parameter(description = "Order identifier", required = true)
            @PathVariable UUID orderId) {
        return service.byOrder(orderId);
    }

    @GetMapping("/{reservationId}")
    @Operation(
            operationId = "getReservationById",
            summary = "Recover a reservation by identifier",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reservation found",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ReservationResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Reservation not found",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class)))
            })
    public ReservationResponse byId(
            @Parameter(description = "Reservation identifier", required = true)
            @PathVariable UUID reservationId) {
        return service.byId(reservationId);
    }

    @PostMapping("/{reservationId}/confirm")
    @Operation(
            operationId = "confirmReservation",
            summary = "Confirm a reservation and sell its places",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reservation confirmed",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ReservationResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Reservation not found",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "409", description = "Reservation not confirmable",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class)))
            })
    public ReservationResponse confirm(
            @Parameter(description = "Reservation identifier", required = true)
            @PathVariable UUID reservationId) {
        return service.confirm(reservationId);
    }

    @PostMapping("/{reservationId}/release")
    @Operation(
            operationId = "releaseReservation",
            summary = "Release a reservation and return its places",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reservation released",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ReservationResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Reservation not found",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "409", description = "Reservation not releasable",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class)))
            })
    public ReservationResponse release(
            @Parameter(description = "Reservation identifier", required = true)
            @PathVariable UUID reservationId) {
        return service.release(reservationId);
    }
}
