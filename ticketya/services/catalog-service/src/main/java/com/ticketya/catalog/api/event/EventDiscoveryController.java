package com.ticketya.catalog.api.event;

import com.ticketya.catalog.application.event.EventDiscoveryService;
import com.ticketya.catalog.application.event.EventDiscoveryService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@Validated
@Tag(name = "Public event discovery", description = "Search and view published event snapshots without identity headers")
public class EventDiscoveryController {
    private final EventDiscoveryService discovery;

    public EventDiscoveryController(EventDiscoveryService discovery) { this.discovery = discovery; }

    @GetMapping
    @Operation(summary = "Search published events", description = "Filters combine with AND. Dates are inclusive. Page is zero based and size is 1..50. Sort: date, price, relevance; direction: asc or desc.", responses = {
            @ApiResponse(responseCode = "200", description = "Paged public event results", content = @Content(examples = @ExampleObject(value = "{\"items\":[{\"id\":\"e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b\",\"name\":\"Summer Live\",\"eventType\":\"MUSIC\",\"startsAt\":\"2030-01-01T20:00:00-03:00\",\"city\":\"Buenos Aires\",\"minimumPrice\":25.50}],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}"))),
            @ApiResponse(responseCode = "400", description = "Invalid date range, pagination, sort, or missing relevance text") })
    public SearchPage search(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID organizerId,
            @RequestParam(required = false) String participant,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer size,
            @RequestParam(defaultValue = "date") String sort,
            @RequestParam(required = false) String direction) {
        return discovery.search(new SearchCriteria(type, organizerId, participant, city, from, to, q, page, size, sort, direction));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a published event snapshot", description = "Returns immutable publication data. Ticket availability is not provided.", responses = {
            @ApiResponse(responseCode = "200", description = "Published event snapshot", content = @Content(examples = @ExampleObject(value = "{\"id\":\"e88d7b2d-9019-4c3c-8bc4-e0ecc43c955b\",\"name\":\"Summer Live\",\"eventType\":\"MUSIC\",\"startsAt\":\"2030-01-01T20:00:00-03:00\",\"venueName\":\"River Hall\",\"city\":\"Buenos Aires\",\"participants\":[\"The Example Band\"],\"sectors\":[{\"name\":\"General\",\"type\":\"GENERAL_ADMISSION\",\"capacity\":500,\"price\":25.50}]}"))),
            @ApiResponse(responseCode = "404", description = "Event does not exist or is not publicly visible") })
    public EventDetail detail(@Parameter(description = "Event UUID") @PathVariable UUID id) { return discovery.detail(id); }
}
