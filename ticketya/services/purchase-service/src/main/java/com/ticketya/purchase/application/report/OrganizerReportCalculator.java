package com.ticketya.purchase.application.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reglas puras del reporte del organizador (US-F12), sin E/S.
 *
 * <ul>
 *   <li>{@code soldTickets}: unidades de órdenes pagadas ({@code CONFIRMED} o
 *       {@code PAID_PENDING_TICKETS}); incluye las luego devueltas.</li>
 *   <li>{@code refundedTickets}: entradas con reintegro {@code COMPLETED}.</li>
 *   <li>{@code activeTickets = sold - refunded}: lugares efectivamente ocupados.</li>
 *   <li>{@code occupancyPercent = active / capacity * 100}, dos decimales, o
 *       {@code null} si la capacidad no está disponible.</li>
 *   <li>{@code netRevenue = gross - refunded}.</li>
 * </ul>
 * Los sectores ofertados sin ventas aparecen con ceros; un sector con ventas
 * que ya no figura en la oferta actual también se incluye.
 */
public final class OrganizerReportCalculator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private OrganizerReportCalculator() {
    }

    public static List<SectorReport> sectors(List<SectorCapacity> offered, List<SectorSales> sales,
                                             List<SectorRefunds> refunds) {
        Map<UUID, SectorCapacity> capacityBySector = new LinkedHashMap<>();
        offered.forEach(sector -> capacityBySector.put(sector.sourceSectorId(), sector));
        Map<UUID, SectorSales> salesBySector = new LinkedHashMap<>();
        sales.forEach(row -> salesBySector.put(row.sourceSectorId(), row));
        Map<UUID, SectorRefunds> refundsBySector = new LinkedHashMap<>();
        refunds.forEach(row -> refundsBySector.put(row.sourceSectorId(), row));

        List<UUID> keys = new ArrayList<>(capacityBySector.keySet());
        salesBySector.keySet().stream().filter(key -> !capacityBySector.containsKey(key)).forEach(keys::add);

        List<SectorReport> result = new ArrayList<>();
        for (UUID key : keys) {
            SectorCapacity capacity = capacityBySector.get(key);
            SectorSales sold = salesBySector.get(key);
            SectorRefunds refunded = refundsBySector.get(key);
            int soldTickets = sold == null ? 0 : sold.soldTickets();
            int refundedTickets = refunded == null ? 0 : Math.min(refunded.refundedTickets(), soldTickets);
            int active = Math.max(0, soldTickets - refundedTickets);
            BigDecimal gross = money(sold == null ? null : sold.grossRevenue());
            BigDecimal refundedAmount = money(refunded == null ? null : refunded.refundedAmount());
            Integer sectorCapacity = capacity == null ? null : capacity.capacity();
            result.add(new SectorReport(
                    capacity != null && capacity.sectorId() != null ? capacity.sectorId()
                            : sold == null ? null : sold.sectorId(),
                    key,
                    capacity != null && capacity.sectorName() != null ? capacity.sectorName()
                            : sold == null ? null : sold.sectorName(),
                    capacity != null && capacity.sectorType() != null ? capacity.sectorType()
                            : sold == null ? null : sold.sectorType(),
                    capacity == null ? null : capacity.unitPrice(),
                    sectorCapacity,
                    soldTickets,
                    refundedTickets,
                    active,
                    occupancy(active, sectorCapacity),
                    gross,
                    refundedAmount,
                    gross.subtract(refundedAmount)));
        }
        result.sort(Comparator.comparing(SectorReport::sectorName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    public static ReportTotals totals(List<SectorReport> sectors) {
        boolean capacityKnown = !sectors.isEmpty() && sectors.stream().allMatch(s -> s.capacity() != null);
        Integer capacity = capacityKnown ? sectors.stream().mapToInt(SectorReport::capacity).sum() : null;
        int sold = sectors.stream().mapToInt(SectorReport::soldTickets).sum();
        int refunded = sectors.stream().mapToInt(SectorReport::refundedTickets).sum();
        int active = sectors.stream().mapToInt(SectorReport::activeTickets).sum();
        BigDecimal gross = sectors.stream().map(SectorReport::grossRevenue).reduce(ZERO, BigDecimal::add);
        BigDecimal refundedAmount = sectors.stream().map(SectorReport::refundedAmount).reduce(ZERO, BigDecimal::add);
        return new ReportTotals(capacity, sold, refunded, active, occupancy(active, capacity),
                gross, refundedAmount, gross.subtract(refundedAmount));
    }

    static BigDecimal occupancy(int active, Integer capacity) {
        if (capacity == null || capacity <= 0) {
            return null;
        }
        return BigDecimal.valueOf(active).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(capacity), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
