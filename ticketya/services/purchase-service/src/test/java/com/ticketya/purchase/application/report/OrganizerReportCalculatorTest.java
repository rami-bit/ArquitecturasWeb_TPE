package com.ticketya.purchase.application.report;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Reglas de negocio del reporte del organizador (US-F12). */
class OrganizerReportCalculatorTest {
    private final UUID campo = UUID.randomUUID();
    private final UUID platea = UUID.randomUUID();
    private final UUID vip = UUID.randomUUID();

    @Test
    void computesSoldRefundedOccupancyAndRevenuePerSectorAndTotals() {
        List<SectorReport> sectors = OrganizerReportCalculator.sectors(
                List.of(new SectorCapacity(campo, UUID.randomUUID(), "Campo", "GENERAL_ADMISSION", 100,
                                new BigDecimal("5000")),
                        new SectorCapacity(platea, UUID.randomUUID(), "Platea", "NUMBERED", 40,
                                new BigDecimal("12000")),
                        new SectorCapacity(vip, UUID.randomUUID(), "VIP", "NUMBERED", 10, new BigDecimal("30000"))),
                List.of(new SectorSales(campo, null, "Campo", "GENERAL_ADMISSION", 10, new BigDecimal("50000")),
                        new SectorSales(platea, null, "Platea", "NUMBERED", 3, new BigDecimal("36000"))),
                List.of(new SectorRefunds(campo, 2, new BigDecimal("10000"))));

        assertThat(sectors).extracting(SectorReport::sectorName).containsExactly("Campo", "Platea", "VIP");
        SectorReport campoReport = sectors.get(0);
        assertThat(campoReport.soldTickets()).isEqualTo(10);
        assertThat(campoReport.refundedTickets()).isEqualTo(2);
        assertThat(campoReport.activeTickets()).isEqualTo(8);
        assertThat(campoReport.occupancyPercent()).isEqualByComparingTo("8.00");
        assertThat(campoReport.grossRevenue()).isEqualByComparingTo("50000");
        assertThat(campoReport.refundedAmount()).isEqualByComparingTo("10000");
        assertThat(campoReport.netRevenue()).isEqualByComparingTo("40000");

        SectorReport plateaReport = sectors.get(1);
        assertThat(plateaReport.occupancyPercent()).isEqualByComparingTo("7.50");
        assertThat(plateaReport.refundedTickets()).isZero();

        SectorReport vipReport = sectors.get(2);
        assertThat(vipReport.soldTickets()).isZero();
        assertThat(vipReport.occupancyPercent()).isEqualByComparingTo("0.00");
        assertThat(vipReport.netRevenue()).isEqualByComparingTo("0");
        assertThat(vipReport.unitPrice()).isEqualByComparingTo("30000");

        ReportTotals totals = OrganizerReportCalculator.totals(sectors);
        assertThat(totals.capacity()).isEqualTo(150);
        assertThat(totals.soldTickets()).isEqualTo(13);
        assertThat(totals.refundedTickets()).isEqualTo(2);
        assertThat(totals.activeTickets()).isEqualTo(11);
        assertThat(totals.occupancyPercent()).isEqualByComparingTo("7.33");
        assertThat(totals.grossRevenue()).isEqualByComparingTo("86000");
        assertThat(totals.netRevenue()).isEqualByComparingTo("76000");
    }

    @Test
    void unknownCapacityYieldsNullOccupancyWithoutLosingSales() {
        List<SectorReport> sectors = OrganizerReportCalculator.sectors(
                List.of(new SectorCapacity(campo, null, null, null, null, new BigDecimal("5000"))),
                List.of(new SectorSales(campo, UUID.randomUUID(), "Campo", "GENERAL_ADMISSION", 4,
                        new BigDecimal("20000"))),
                List.of());

        assertThat(sectors).singleElement().satisfies(sector -> {
            assertThat(sector.sectorName()).isEqualTo("Campo");
            assertThat(sector.capacity()).isNull();
            assertThat(sector.occupancyPercent()).isNull();
            assertThat(sector.soldTickets()).isEqualTo(4);
        });
        ReportTotals totals = OrganizerReportCalculator.totals(sectors);
        assertThat(totals.capacity()).isNull();
        assertThat(totals.occupancyPercent()).isNull();
        assertThat(totals.grossRevenue()).isEqualByComparingTo("20000");
    }

    @Test
    void salesOfSectorsNoLongerOfferedAreStillReportedAndRefundsNeverExceedSales() {
        List<SectorReport> sectors = OrganizerReportCalculator.sectors(
                List.of(),
                List.of(new SectorSales(vip, UUID.randomUUID(), "VIP", "NUMBERED", 1, new BigDecimal("30000"))),
                List.of(new SectorRefunds(vip, 3, new BigDecimal("30000"))));

        assertThat(sectors).singleElement().satisfies(sector -> {
            assertThat(sector.refundedTickets()).isEqualTo(1);
            assertThat(sector.activeTickets()).isZero();
            assertThat(sector.netRevenue()).isEqualByComparingTo("0");
        });
        assertThat(OrganizerReportCalculator.totals(List.of()).occupancyPercent()).isNull();
    }

    @Test
    void occupancyIsRoundedHalfUpToTwoDecimals() {
        assertThat(OrganizerReportCalculator.occupancy(1, 3)).isEqualByComparingTo("33.33");
        assertThat(OrganizerReportCalculator.occupancy(2, 3)).isEqualByComparingTo("66.67");
        assertThat(OrganizerReportCalculator.occupancy(5, 0)).isNull();
    }
}
