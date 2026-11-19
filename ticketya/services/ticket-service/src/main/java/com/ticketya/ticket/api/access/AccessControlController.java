package com.ticketya.ticket.api.access;

import com.ticketya.ticket.api.error.ApiProblemException;
import com.ticketya.ticket.api.tracing.CorrelationIdFilter;
import com.ticketya.ticket.application.access.AccessRejectedException;
import com.ticketya.ticket.application.access.AccessResult;
import com.ticketya.ticket.application.access.AccessValidationService;
import com.ticketya.ticket.infrastructure.audit.AuditEvent;
import com.ticketya.ticket.infrastructure.audit.AuditPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * US-F8: control de acceso con QR, publicado por el Gateway en
 * {@code /api/access/**}. El escaneo exige rol {@code ACCESS_STAFF}; la
 * consulta de intentos admite además {@code ADMIN}.
 */
@RestController
@RequestMapping("/api/access/events/{eventId}")
@Tag(name = "Access control", description = "Validación de entradas QR en el ingreso al evento")
public class AccessControlController {
    private final AccessValidationService service;
    private final AuditPublisher audit;

    public AccessControlController(AccessValidationService service, AuditPublisher audit) {
        this.service = service;
        this.audit = audit;
    }

    @PostMapping("/validations")
    @Operation(operationId = "validateTicketAccess", summary = "Validar un QR y registrar el ingreso")
    @ApiResponse(responseCode = "200", description = "Entrada válida; queda marcada como utilizada")
    @ApiResponse(responseCode = "400", description = "Solicitud inválida")
    @ApiResponse(responseCode = "403", description = "Rol distinto de ACCESS_STAFF")
    @ApiResponse(responseCode = "404", description = "QR inválido o entrada inexistente (UNKNOWN_TICKET)")
    @ApiResponse(responseCode = "409", description = "Entrada ya utilizada (ALREADY_USED) o revocada (REVOKED)")
    @ApiResponse(responseCode = "422", description = "La entrada corresponde a otro evento (WRONG_EVENT)")
    public AccessValidationResponse validate(
            @PathVariable("eventId") UUID eventId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody AccessValidationRequest request,
            HttpServletRequest http) {
        UUID staffId = identity(userId);
        if (!"ACCESS_STAFF".equals(role)) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "Forbidden", "ACCESS_STAFF role required");
        }
        String correlationId = CorrelationIdFilter.get(http);
        try {
            AccessValidationResponse admitted =
                    service.validate(eventId, request.qrPayload(), request.gate(), staffId, correlationId);
            publish(correlationId, AccessResult.ADMITTED, staffId, eventId, admitted.ticketId(), request.gate());
            return admitted;
        } catch (AccessRejectedException rejected) {
            publish(correlationId, rejected.reason(), staffId, eventId, rejected.ticketId(), request.gate());
            throw rejected;
        }
    }

    @GetMapping("/scans")
    @Operation(operationId = "listAccessScans", summary = "Consultar los intentos de ingreso de un evento")
    @ApiResponse(responseCode = "200", description = "Intentos más recientes primero, con totales por resultado")
    @ApiResponse(responseCode = "400", description = "Paginación inválida")
    @ApiResponse(responseCode = "403", description = "Rol distinto de ACCESS_STAFF o ADMIN")
    public AccessScanPage scans(
            @PathVariable("eventId") UUID eventId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        identity(userId);
        if (!List.of("ACCESS_STAFF", "ADMIN").contains(role)) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "Forbidden", "ACCESS_STAFF or ADMIN role required");
        }
        try {
            return service.scans(eventId, page, size);
        } catch (IllegalArgumentException invalid) {
            throw ApiProblemException.badRequest(invalid.getMessage());
        }
    }

    @ExceptionHandler(AccessRejectedException.class)
    public ResponseEntity<AccessProblem> rejected(AccessRejectedException rejected, HttpServletRequest request) {
        HttpStatus status = rejected.reason().status();
        AccessProblem body = new AccessProblem(URI.create("about:blank"), "Access denied", status.value(),
                rejected.getMessage(), URI.create(request.getRequestURI()), CorrelationIdFilter.get(request),
                rejected.reason().name(), rejected.ticketId(), rejected.usedAt(), rejected.usedGate());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private void publish(String correlationId, AccessResult result, UUID staffId, UUID eventId, UUID ticketId,
                         String gate) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", eventId.toString());
        details.put("gate", gate);
        audit.publish(AuditEvent.of(correlationId, "ACCESS_VALIDATION", result.name(), staffId.toString(),
                "ACCESS_STAFF", "TICKET", ticketId, details));
    }

    private static UUID identity(String userId) {
        try {
            if (userId == null || userId.isBlank()) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(userId.trim());
        } catch (IllegalArgumentException invalid) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "Forbidden", "A valid identity is required");
        }
    }
}
