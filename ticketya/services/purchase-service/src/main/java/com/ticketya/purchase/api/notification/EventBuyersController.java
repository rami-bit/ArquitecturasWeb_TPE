package com.ticketya.purchase.api.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * PDF-F11: contrato interno de lectura para Notification Service. Devuelve los
 * clientes distintos con una compra pagada del evento, para avisarles de una
 * suspensión, reanudación o cancelación. No expone órdenes, montos ni tickets,
 * y el Gateway no lo publica.
 */
@RestController
@RequestMapping("/internal/events")
@Tag(name = "Event buyers (internal)", description = "Compradores de un evento para notificaciones")
public class EventBuyersController {
    private final JdbcTemplate jdbc;

    public EventBuyersController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{eventId}/buyers")
    @Operation(operationId = "listEventBuyers", summary = "Clientes con compras pagadas de un evento")
    @ApiResponse(responseCode = "200", description = "Lista de clientes (vacía si no hay ventas)")
    public EventBuyers buyers(@PathVariable("eventId") UUID eventId) {
        List<UUID> customers = jdbc.queryForList("""
                SELECT DISTINCT customer_id FROM purchase_orders
                WHERE event_id = ? AND status IN ('CONFIRMED', 'PAID_PENDING_TICKETS')
                ORDER BY customer_id
                """, UUID.class, eventId);
        return new EventBuyers(eventId, customers);
    }

    @Schema(name = "EventBuyers")
    public record EventBuyers(UUID eventId, List<UUID> customerIds) {
    }
}
