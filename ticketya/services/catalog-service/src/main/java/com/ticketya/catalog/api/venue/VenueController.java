package com.ticketya.catalog.api.venue;

import com.ticketya.catalog.application.venue.VenueManagementService;
import com.ticketya.catalog.domain.venue.SectorDefinition;
import com.ticketya.catalog.domain.venue.SectorType;
import com.ticketya.catalog.domain.venue.SeatPosition;
import com.ticketya.catalog.domain.venue.Venue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/venues")
@Tag(name = "Venues", description = "Canonical venue templates")
public class VenueController {
    private final VenueManagementService service;
    public VenueController(VenueManagementService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse create(@RequestHeader(value = "X-User-Role", required = false) String role,
                                @Valid @RequestBody VenueRequest request) {
        return VenueResponse.from(service.create(role, request.name(), request.address(), request.city(),
                request.latitude(), request.longitude(), definitions(request.sectors())));
    }

    @PutMapping("/{id}")
    public VenueResponse update(@RequestHeader(value = "X-User-Role", required = false) String role,
                                @PathVariable UUID id, @Valid @RequestBody VenueRequest request) {
        return VenueResponse.from(service.update(role, id, request.name(), request.address(), request.city(),
                request.latitude(), request.longitude(), definitions(request.sectors())));
    }

    @GetMapping
    @Operation(summary = "List venues", description = "Returns a stable, paginated list for ORGANIZER and ADMIN users.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Venue page",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = VenuePageResponse.class),
                                    examples = @ExampleObject(name = "Numbered and general admission", value = "{\"content\":[{\"name\":\"Arena\",\"sectors\":[{\"type\":\"NUMBERED\",\"capacity\":1},{\"type\":\"GENERAL_ADMISSION\",\"capacity\":50}]}],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}"))),
                    @ApiResponse(responseCode = "400", description = "Invalid pagination"),
                    @ApiResponse(responseCode = "403", description = "Role is not permitted")
            })
    public VenuePageResponse list(@RequestHeader(value = "X-User-Role", required = false) String role,
                                  @Parameter(description = "Zero-based page index", example = "0") @RequestParam(defaultValue = "0") int page,
                                  @Parameter(description = "Items per page (1 to 100)", example = "20") @RequestParam(defaultValue = "20") int size) {
        Page<Venue> result = service.list(role, page, size);
        return new VenuePageResponse(result.getContent().stream().map(VenueResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private List<SectorDefinition> definitions(List<SectorRequest> requests) {
        return requests == null ? null : requests.stream().map(s -> new SectorDefinition(
                s.name(), s.type(), s.capacity(), s.seats() == null ? List.of() : s.seats().stream()
                .map(p -> new SeatPosition(p.rowLabel(), p.seatNumber())).toList())).toList();
    }

    public record VenueRequest(@NotBlank String name, @NotBlank String address, @NotBlank String city,
                               @NotNull BigDecimal latitude, @NotNull BigDecimal longitude,
                               List<@Valid SectorRequest> sectors) { }
    public record SectorRequest(@NotBlank String name, @NotNull SectorType type, @Positive Integer capacity,
                                List<@Valid SeatRequest> seats) { }
    public record SeatRequest(@NotBlank String rowLabel, @Positive int seatNumber) { }
    public record VenueResponse(UUID id, String name, String address, String city, BigDecimal latitude,
                                BigDecimal longitude, List<SectorResponse> sectors) {
        static VenueResponse from(Venue v) {
            return new VenueResponse(v.getId(), v.getName(), v.getAddress(), v.getCity(), v.getLatitude(),
                    v.getLongitude(), v.getSectors().stream().map(s -> new SectorResponse(s.getId(), s.getName(),
                    s.getType(), s.getCapacity(), s.getSeats().stream().map(x -> new SeatResponse(
                    x.getRowLabel(), x.getSeatNumber())).toList())).toList());
        }
    }
    public record SectorResponse(UUID id, String name, SectorType type, int capacity, List<SeatResponse> seats) { }
    public record SeatResponse(String rowLabel, int seatNumber) { }
    public record VenuePageResponse(List<VenueResponse> content, int page, int size, long totalElements, int totalPages) { }
}
