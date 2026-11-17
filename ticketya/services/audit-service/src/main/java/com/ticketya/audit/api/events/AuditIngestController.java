package com.ticketya.audit.api.events;

import com.ticketya.audit.application.AuditEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato interno de ingesta. Sólo es accesible dentro de la red de Compose:
 * el Gateway no publica {@code /internal/**}.
 */
@RestController
@RequestMapping("/internal/audit-events")
@Tag(name = "Audit ingestion (internal)", description = "Recepción de eventos de auditoría de los microservicios")
public class AuditIngestController {
    private final AuditEventService service;

    public AuditIngestController(AuditEventService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(operationId = "recordAuditEvent", summary = "Registrar un evento de auditoría (idempotente por eventId)")
    @ApiResponse(responseCode = "202", description = "Evento registrado o reintento ya registrado")
    @ApiResponse(responseCode = "400", description = "Evento inválido")
    public ResponseEntity<AuditIngestResponse> record(@Valid @RequestBody AuditEventRequest request) {
        boolean inserted = service.record(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AuditIngestResponse(request.eventId(), !inserted));
    }
}
