package com.ticketya.purchase.application.recommendation;

import com.ticketya.purchase.api.recommendation.CoPurchaseResponse;
import com.ticketya.purchase.api.recommendation.RecommendationSignalsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Consulta de señales para recomendaciones sobre la base propia de Purchase.
 * Sólo cuentan las órdenes pagadas (`CONFIRMED` o `PAID_PENDING_TICKETS`), el
 * mismo criterio del reporte del organizador. No expone montos, pagos ni
 * tickets, y no copia órdenes a otra base.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationSignalsService {

    private static final String PAID_STATUSES = "'CONFIRMED','PAID_PENDING_TICKETS'";
    private static final int MAX_CO_PURCHASES = 200;

    private final JdbcTemplate jdbc;

    public RecommendationSignalsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RecommendationSignalsResponse signals(UUID customerId) {
        List<UUID> purchased = jdbc.query(
                "SELECT DISTINCT event_id FROM purchase_orders WHERE customer_id = ? "
                        + "AND status IN (" + PAID_STATUSES + ")",
                (rs, row) -> rs.getObject("event_id", UUID.class), customerId);
        List<CoPurchaseResponse> coPurchases = purchased.isEmpty()
                ? List.of() : findCoPurchases(customerId, purchased);
        return new RecommendationSignalsResponse(customerId, purchased, coPurchases, Instant.now());
    }

    private List<CoPurchaseResponse> findCoPurchases(UUID customerId, List<UUID> purchased) {
        String placeholders = String.join(",", Collections.nCopies(purchased.size(), "?"));
        String sql = """
                SELECT o.event_id AS event_id, COUNT(DISTINCT o.customer_id) AS co_count
                FROM purchase_orders o
                WHERE o.status IN (%s)
                  AND o.customer_id <> ?
                  AND o.event_id NOT IN (%s)
                  AND EXISTS (
                      SELECT 1 FROM purchase_orders mine
                      WHERE mine.customer_id = o.customer_id
                        AND mine.status IN (%s)
                        AND mine.event_id IN (%s)
                  )
                GROUP BY o.event_id
                ORDER BY co_count DESC, o.event_id ASC
                LIMIT %d
                """.formatted(PAID_STATUSES, placeholders, PAID_STATUSES, placeholders,
                        MAX_CO_PURCHASES);
        List<Object> args = new ArrayList<>();
        args.add(customerId);
        args.addAll(purchased);          // NOT IN (eventos ya comprados)
        args.addAll(purchased);          // IN (eventos del cliente en el EXISTS)
        return jdbc.query(sql,
                (rs, row) -> new CoPurchaseResponse(
                        rs.getObject("event_id", UUID.class), rs.getInt("co_count")),
                args.toArray());
    }
}
