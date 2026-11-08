package com.ticketya.inventory.contract;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El esquema es la última línea de defensa: constraints y checks rechazan duplicados y
 * valores inválidos aunque la capa de aplicación tuviera un error.
 */
@org.testcontainers.junit.jupiter.Testcontainers(disabledWithoutDocker = true)
class InventorySchemaConstraintTest extends InventoryContractTestBase {

    @Test
    void rejectsDuplicateSectorOfTheSameEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        assertThatThrownBy(() -> jdbc().update("""
                        INSERT INTO inventory_sectors
                            (id, event_inventory_id, source_sector_id, name, type,
                             capacity, sold, reserved)
                        SELECT ?, event_inventory_id, source_sector_id, name, type,
                               capacity, sold, reserved
                        FROM inventory_sectors WHERE id = ?
                        """, UUID.randomUUID(), layout.numberedSectorId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsInvalidSectorTypeAndOverCapacityCounters() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        assertThatThrownBy(() -> jdbc().update(
                        "UPDATE inventory_sectors SET type = 'BROKEN' WHERE id = ?",
                        layout.generalSectorId()))
                .isInstanceOf(DataAccessException.class);

        jdbc().update("UPDATE inventory_sectors SET sold = 2, reserved = 0 WHERE id = ?",
                layout.generalSectorId());
        assertThatThrownBy(() -> jdbc().update(
                        "UPDATE inventory_sectors SET capacity = 1 WHERE id = ?",
                        layout.generalSectorId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rejectsInvalidSeatStatusAndDuplicateSeatPosition() throws Exception {
        UUID eventId = UUID.randomUUID();
        Layout layout = initializeAndDiscoverLayout(eventId);

        assertThatThrownBy(() -> jdbc().update(
                        "UPDATE inventory_items SET status = 'BROKEN' WHERE id = ?",
                        layout.seatOneId()))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc().update("""
                        INSERT INTO inventory_items
                            (id, event_inventory_id, sector_id, source_seat_id,
                             row_label, seat_number, status)
                        SELECT ?, event_inventory_id, sector_id, ?, row_label, seat_number, 'AVAILABLE'
                        FROM inventory_items WHERE id = ?
                        """, UUID.randomUUID(), UUID.randomUUID(), layout.seatOneId()))
                .isInstanceOf(DataAccessException.class);
    }
}
