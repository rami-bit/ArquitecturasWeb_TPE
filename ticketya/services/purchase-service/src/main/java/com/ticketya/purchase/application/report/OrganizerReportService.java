package com.ticketya.purchase.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketya.purchase.api.error.ApiProblemException;
import com.ticketya.purchase.infrastructure.client.CatalogClient;
import com.ticketya.purchase.infrastructure.client.InventoryClient;
import com.ticketya.purchase.infrastructure.client.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arma el reporte del organizador (US-F12) combinando tres fuentes:
 * <ol>
 *   <li>Catalog: autoriza (ownership del ORGANIZER o ADMIN), nombre, estado y precios.</li>
 *   <li>Purchase (base propia): ventas y devoluciones por sector.</li>
 *   <li>Inventory: capacidad de cada sector para el porcentaje de ocupación.</li>
 * </ol>
 * Si Inventory no responde, el reporte se entrega igual con la capacidad y la
 * ocupación en {@code null} y una advertencia (degradación controlada): las
 * ventas y la recaudación no dependen de ese servicio.
 */
@Service
public class OrganizerReportService {
    private static final Logger log = LoggerFactory.getLogger(OrganizerReportService.class);
    private static final List<String> REPORT_ROLES = List.of("ORGANIZER", "ADMIN");

    private final CatalogClient catalog;
    private final InventoryClient inventory;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public OrganizerReportService(CatalogClient catalog, InventoryClient inventory, JdbcTemplate jdbc, Clock clock) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public EventReport eventReport(UUID eventId, UUID userId, String role, String correlationId) {
        if (!REPORT_ROLES.contains(role)) {
            throw ApiProblemException.forbidden("Los reportes requieren rol ORGANIZER o ADMIN");
        }
        JsonNode event = authorizedEvent(eventId, userId, role, correlationId);

        List<String> warnings = new ArrayList<>();
        Map<UUID, BigDecimal> prices = new java.util.LinkedHashMap<>();
        for (JsonNode offering : event.path("offerings")) {
            prices.put(UUID.fromString(offering.path("sectorId").asText()), offering.path("price").decimalValue());
        }
        List<SectorCapacity> offered = capacities(eventId, prices, correlationId, warnings);

        List<SectorSales> sales = jdbc.query("""
                SELECT i.source_sector_id, i.inventory_sector_id, i.sector_name, i.sector_type,
                       COUNT(u.id) AS sold, COALESCE(SUM(i.unit_price), 0) AS gross
                FROM purchase_orders o
                JOIN purchase_order_items i ON i.order_id = o.id
                JOIN order_item_units u ON u.order_item_id = i.id
                WHERE o.event_id = ? AND o.status IN ('CONFIRMED', 'PAID_PENDING_TICKETS')
                GROUP BY i.source_sector_id, i.inventory_sector_id, i.sector_name, i.sector_type
                """, (rs, n) -> new SectorSales(
                rs.getObject("source_sector_id", UUID.class),
                rs.getObject("inventory_sector_id", UUID.class),
                rs.getString("sector_name"),
                rs.getString("sector_type"),
                rs.getInt("sold"),
                rs.getBigDecimal("gross")), eventId);

        // Una entrada puede tener una solicitud del cliente y otra de cancelación;
        // sólo cuenta una vez el reintegro completado.
        List<SectorRefunds> refunds = jdbc.query("""
                SELECT i.source_sector_id, COUNT(*) AS refunded, COALESCE(SUM(r.amount), 0) AS amount
                FROM (SELECT DISTINCT ON (ticket_id) ticket_id, order_item_id, amount
                      FROM refund_requests
                      WHERE event_id = ? AND progress = 'COMPLETED'
                      ORDER BY ticket_id, created_at) r
                JOIN purchase_order_items i ON i.id = r.order_item_id
                GROUP BY i.source_sector_id
                """, (rs, n) -> new SectorRefunds(
                rs.getObject("source_sector_id", UUID.class),
                rs.getInt("refunded"),
                rs.getBigDecimal("amount")), eventId);

        List<SectorReport> sectors = OrganizerReportCalculator.sectors(offered, sales, refunds);
        boolean capacityAvailable = warnings.isEmpty();
        return new EventReport(
                eventId,
                event.path("name").asText(null),
                event.path("status").asText(null),
                parse(event.path("startsAt").asText(null)),
                uuidOrNull(event.path("organizerId").asText(null)),
                "ARS",
                OffsetDateTime.now(clock),
                capacityAvailable,
                sectors,
                OrganizerReportCalculator.totals(sectors),
                warnings);
    }

    private JsonNode authorizedEvent(UUID eventId, UUID userId, String role, String correlationId) {
        try {
            return catalog.getManagedEvent(eventId, userId, role, correlationId);
        } catch (ServiceException failure) {
            int status = failure.status() == null ? 503 : failure.status().value();
            if (status == 404) {
                throw ApiProblemException.notFound("Evento no encontrado");
            }
            if (status == 403) {
                throw ApiProblemException.forbidden("El evento no pertenece al organizador");
            }
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable",
                    "Catalog no disponible para autorizar el reporte");
        }
    }

    private List<SectorCapacity> capacities(UUID eventId, Map<UUID, BigDecimal> prices, String correlationId,
                                            List<String> warnings) {
        List<SectorCapacity> result = new ArrayList<>();
        try {
            JsonNode availability = inventory.getAvailability(eventId, correlationId);
            for (JsonNode sector : availability.path("sectors")) {
                UUID source = UUID.fromString(sector.path("sourceSectorId").asText());
                result.add(new SectorCapacity(source,
                        uuidOrNull(sector.path("sectorId").asText(null)),
                        sector.path("name").asText(null),
                        sector.path("type").asText(null),
                        sector.path("capacity").asInt(),
                        prices.get(source)));
            }
            return result;
        } catch (ServiceException | IllegalArgumentException failure) {
            log.warn("Inventory unavailable for report eventId={} correlationId={}: {}",
                    eventId, correlationId, failure.getMessage());
            warnings.add("INVENTORY_UNAVAILABLE: capacidad y ocupación no disponibles");
            prices.forEach((source, price) -> result.add(new SectorCapacity(source, null, null, null, null, price)));
            return result;
        }
    }

    private static UUID uuidOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static OffsetDateTime parse(String value) {
        try {
            return value == null || value.isBlank() ? null : OffsetDateTime.parse(value);
        } catch (RuntimeException invalid) {
            return null;
        }
    }
}
