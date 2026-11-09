package com.ticketya.ticket.api.issuance;

import com.ticketya.ticket.api.error.Problem;
import com.ticketya.ticket.application.TicketIssuanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/orders/{orderId}/items/{orderItemId}/tickets")
@Tag(name = "Ticket issuance (internal)",
        description = "Internal ticket issuance command consumed by Purchase Service")
public class TicketIssuanceController {
    private final TicketIssuanceService service;

    public TicketIssuanceController(TicketIssuanceService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            operationId = "issueTicketsForOrderItem",
            summary = "Issue or recover QR tickets for a confirmed order item",
            description = "Purchase calls this command only after payment and inventory "
                    + "confirmation. The idempotency key is orderId + orderItemId.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Tickets issued",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = TicketIssueResponse.class))),
                    @ApiResponse(responseCode = "200", description = "Idempotent replay",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = TicketIssueResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class))),
                    @ApiResponse(responseCode = "409",
                            description = "The order item was already issued with different data",
                            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = Problem.class)))
            })
    public ResponseEntity<TicketIssueResponse> issue(
            @PathVariable UUID orderId,
            @PathVariable UUID orderItemId,
            @Valid @RequestBody TicketIssueRequest request) {
        TicketIssuanceService.IssueResult result =
                service.issue(orderId, orderItemId, request);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }
}
