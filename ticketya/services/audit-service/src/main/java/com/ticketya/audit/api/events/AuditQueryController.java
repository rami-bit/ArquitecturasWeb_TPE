package com.ticketya.audit.api.events;

import com.ticketya.audit.api.error.ApiProblemException;
import com.ticketya.audit.application.AuditEventService;
import com.ticketya.audit.domain.AuditSearchCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Consulta de la bitácora, publicada por el Gateway en {@code /api/audit/**}.
 * Sólo ADMIN: el Gateway lo exige por rol y el servicio lo vuelve a comprobar
 * con la identidad derivada ({@code X-User-Role}).
 */
@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit queries", description = "Consulta de la bitácora centralizada (ADMIN)")
public class AuditQueryController {
    private final AuditEventService service;

    public AuditQueryController(AuditEventService service) {
        this.service = service;
    }

    @GetMapping("/events")
    @Operation(operationId = "searchAuditEvents", summary = "Buscar eventos de auditoría por filtros")
    @ApiResponse(responseCode = "200", description = "Página de eventos, más recientes primero")
    @ApiResponse(responseCode = "400", description = "Filtros inválidos")
    @ApiResponse(responseCode = "403", description = "Rol distinto de ADMIN")
    public AuditEventPage search(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "correlationId", required = false) String correlationId,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "outcome", required = false) String outcome,
            @RequestParam(value = "resourceId", required = false) String resourceId,
            @RequestParam(value = "actorId", required = false) String actorId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        requireAdmin(role);
        AuditSearchCriteria criteria;
        try {
            criteria = new AuditSearchCriteria(correlationId, service, operation, outcome, resourceId,
                    actorId, from, to, page, size);
        } catch (IllegalArgumentException exception) {
            throw ApiProblemException.badRequest(exception.getMessage());
        }
        return this.service.search(criteria);
    }

    @GetMapping("/traces/{correlationId}")
    @Operation(operationId = "getAuditTrace",
            summary = "Seguir una operación a través de los microservicios por su X-Correlation-Id")
    @ApiResponse(responseCode = "200", description = "Eventos de la operación en orden cronológico")
    @ApiResponse(responseCode = "403", description = "Rol distinto de ADMIN")
    @ApiResponse(responseCode = "404", description = "No hay eventos para ese identificador")
    public AuditTrace trace(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable("correlationId") String correlationId) {
        requireAdmin(role);
        return service.trace(correlationId);
    }

    private static void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw ApiProblemException.forbidden("ADMIN role required");
        }
    }
}
