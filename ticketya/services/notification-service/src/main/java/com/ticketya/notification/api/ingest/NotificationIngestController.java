package com.ticketya.notification.api.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ticketya.notification.api.error.ApiProblemException;
import com.ticketya.notification.api.tracing.CorrelationIdFilter;
import com.ticketya.notification.application.IngestService;
import com.ticketya.notification.application.TriggerType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contratos internos de ingesta (fire-and-forget). Responden {@code 202} apenas
 * registran el hecho: la resolución de destinatarios y la entrega ocurren
 * después, fuera de la solicitud del productor. No se publican por el Gateway.
 */
@RestController
@RequestMapping("/internal/notifications")
@Tag(name = "Notification ingestion (internal)", description = "Hechos de negocio que generan notificaciones")
public class NotificationIngestController {
    private final IngestService service;
    private final ObjectMapper mapper;

    public NotificationIngestController(IngestService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(operationId = "recordOrderNotification",
            summary = "Registrar una compra confirmada o cancelada (idempotente por tipo y orden)")
    @ApiResponse(responseCode = "202", description = "Registrado o reintento ya registrado")
    @ApiResponse(responseCode = "400", description = "Hecho inválido")
    public ResponseEntity<NotificationIngestResponse> order(@Valid @RequestBody OrderNotificationRequest request,
                                                            HttpServletRequest http) {
        TriggerType type = TriggerType.parse(request.type(), TriggerType.ORDER_TYPES);
        if (type == null) {
            throw ApiProblemException.badRequest("type must be ORDER_CONFIRMED or ORDER_CANCELLED");
        }
        String correlationId = correlation(request.correlationId(), http);
        ObjectNode payload = mapper.valueToTree(request);
        payload.put("correlationId", correlationId);
        IngestService.IngestResult result = service.ingest(type, request.orderId().toString(), null, payload,
                correlationId);
        return accepted(result, type);
    }

    @PostMapping("/event-changes")
    @Operation(operationId = "recordEventChangeNotification",
            summary = "Registrar la publicación, suspensión, reanudación o cancelación de un evento")
    @ApiResponse(responseCode = "202", description = "Registrado o reintento ya registrado")
    @ApiResponse(responseCode = "400", description = "Hecho inválido")
    public ResponseEntity<NotificationIngestResponse> eventChange(
            @Valid @RequestBody EventChangeNotificationRequest request, HttpServletRequest http) {
        TriggerType type = TriggerType.parse(request.type(), TriggerType.EVENT_TYPES);
        if (type == null) {
            throw ApiProblemException.badRequest(
                    "type must be EVENT_PUBLISHED, EVENT_SUSPENDED, EVENT_RESUMED or EVENT_CANCELLED");
        }
        String correlationId = correlation(request.correlationId(), http);
        ObjectNode payload = mapper.valueToTree(request);
        payload.put("correlationId", correlationId);
        IngestService.IngestResult result = service.ingest(type, request.eventId().toString(), request.changeId(),
                payload, correlationId);
        return accepted(result, type);
    }

    private static ResponseEntity<NotificationIngestResponse> accepted(IngestService.IngestResult result,
                                                                       TriggerType type) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new NotificationIngestResponse(result.triggerId(), type.name(), result.duplicate()));
    }

    private static String correlation(String fromBody, HttpServletRequest http) {
        return fromBody != null && !fromBody.isBlank() ? fromBody.trim() : CorrelationIdFilter.get(http);
    }
}
