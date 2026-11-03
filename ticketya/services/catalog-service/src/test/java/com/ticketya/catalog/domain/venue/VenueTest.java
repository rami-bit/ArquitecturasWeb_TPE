package com.ticketya.catalog.domain.venue;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VenueTest {
    @Test
    void derivesNumberedCapacityAndKeepsGeneralAdmissionCapacity() {
        Venue venue = venue();

        VenueSector numbered = venue.addNumberedSector(
                "Platea",
                List.of(new SeatPosition("A", 1), new SeatPosition("A", 2)));
        VenueSector general = venue.addGeneralAdmissionSector("Campo", 500);

        assertThat(numbered.getType()).isEqualTo(SectorType.NUMBERED);
        assertThat(numbered.getCapacity()).isEqualTo(2);
        assertThat(general.getType()).isEqualTo(SectorType.GENERAL_ADMISSION);
        assertThat(general.getCapacity()).isEqualTo(500);
        assertThat(general.getSeats()).isEmpty();
    }

    @Test
    void rejectsDuplicateSeatsWithoutAddingPartialSector() {
        Venue venue = venue();
        SeatPosition duplicate = new SeatPosition("A", 1);

        assertThatThrownBy(() -> venue.addNumberedSector("Platea", List.of(duplicate, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
        assertThat(venue.getSectors()).isEmpty();
    }

    @Test
    void rejectsInvalidGeneralAdmissionCapacityAndCoordinates() {
        Venue venue = venue();

        assertThatThrownBy(() -> venue.addGeneralAdmissionSector("Campo", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> new Venue(
                "Estadio",
                "Av. Siempre Viva 123",
                "Buenos Aires",
                BigDecimal.valueOf(91),
                BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude");
    }

    private Venue venue() {
        return new Venue(
                "Estadio",
                "Av. Siempre Viva 123",
                "Buenos Aires",
                BigDecimal.valueOf(-34.6037),
                BigDecimal.valueOf(-58.3816));
    }
}
