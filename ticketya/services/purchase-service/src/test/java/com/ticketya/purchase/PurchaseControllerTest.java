package com.ticketya.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class PurchaseControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ticketRequestAllowsUnnumberedSectorWithoutRowOrSeat() throws Exception {
        var order = mapper.readTree("""
                {"customer_id":"customer-1","event_id":"event-1","event_name":"Show",
                 "event_starts_at":"2026-12-01T20:00:00Z","venue_name":"Hall","venue_city":"City"}
                """);
        Map<String, Object> line = Map.of("sectorName", "General admission");
        Map<String, Object> unit = new java.util.LinkedHashMap<>();
        unit.put("unitNumber", 1);
        unit.put("rowLabel", null);
        unit.put("seatNumber", null);

        Map<String, Object> body = assertDoesNotThrow(
                () -> PurchaseController.ticketBody(order, line, List.of(unit)));

        assertNull(unit.get("rowLabel"));
        assertNull(unit.get("seatNumber"));
        assertDoesNotThrow(() -> mapper.writeValueAsString(body));
    }
}
