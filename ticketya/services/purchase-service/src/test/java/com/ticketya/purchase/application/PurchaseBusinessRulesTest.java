package com.ticketya.purchase.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.purchase.api.error.ApiProblemException;
import com.ticketya.purchase.infrastructure.client.CatalogClient;
import com.ticketya.purchase.infrastructure.client.InventoryClient;
import com.ticketya.purchase.infrastructure.client.PaymentClient;
import com.ticketya.purchase.infrastructure.client.TicketClient;
import com.ticketya.purchase.infrastructure.persistence.JdbcPurchaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for purchase eligibility rules, independent of the HTTP layer. */
class PurchaseBusinessRulesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private CatalogClient catalog;
    private InventoryClient inventory;
    private PaymentClient payment;
    private JdbcPurchaseRepository repository;
    private PurchaseService service;
    private final UUID eventId = UUID.randomUUID();
    private final UUID sourceSectorId = UUID.randomUUID();
    private final UUID inventorySectorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        catalog = mock(CatalogClient.class);
        inventory = mock(InventoryClient.class);
        payment = mock(PaymentClient.class);
        repository = mock(JdbcPurchaseRepository.class);
        service = new PurchaseService(mock(JdbcTemplate.class), mapper, catalog, inventory, payment,
                mock(TicketClient.class), repository, 6, 600);
    }

    @Test
    void generalAdmissionCannotBeBoughtBySelectingSeats() throws Exception {
        when(catalog.getPurchaseSnapshot(eventId, "corr"))
                .thenReturn(mapper.readTree(("{\"sectors\":[{\"sourceSectorId\":\"%s\","
                        + "\"type\":\"GENERAL_ADMISSION\",\"price\":2500}]}" ).formatted(sourceSectorId)));
        when(inventory.getAvailability(eventId, "corr"))
                .thenReturn(mapper.readTree(("{\"sectors\":[{\"sectorId\":\"%s\","
                        + "\"sourceSectorId\":\"%s\",\"seats\":[] }]}" )
                        .formatted(inventorySectorId, sourceSectorId)));
        UUID seatId = UUID.randomUUID();
        ApiProblemException error = assertThrows(ApiProblemException.class, () -> service.createOrder(
                UUID.randomUUID(), "CUSTOMER", "key", "corr",
                ("{\"eventId\":\"%s\",\"items\":[{\"sectorId\":\"%s\","
                        + "\"seatIds\":[\"%s\"]}],\"payment\":{\"method\":\"CARD\","
                        + "\"token\":\"tok_approved\"}}")
                        .formatted(eventId, inventorySectorId, seatId)));

        assertEquals(400, error.getStatus().value());
        verify(payment, never()).charge(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(repository, never()).insertOrder(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void numberedSectorCannotBePurchasedAsUnassignedQuantity() throws Exception {
        when(catalog.getPurchaseSnapshot(eventId, "corr"))
                .thenReturn(mapper.readTree(("{\"sectors\":[{\"sourceSectorId\":\"%s\","
                        + "\"type\":\"NUMBERED\",\"price\":2500,\"seats\":[]}]}" )
                        .formatted(sourceSectorId)));
        when(inventory.getAvailability(eventId, "corr"))
                .thenReturn(mapper.readTree(("{\"sectors\":[{\"sectorId\":\"%s\","
                        + "\"sourceSectorId\":\"%s\",\"seats\":[]}]}" )
                        .formatted(inventorySectorId, sourceSectorId)));
        ApiProblemException error = assertThrows(ApiProblemException.class, () -> service.createOrder(
                UUID.randomUUID(), "CUSTOMER", "key", "corr",
                ("{\"eventId\":\"%s\",\"items\":[{\"sectorId\":\"%s\","
                        + "\"quantity\":2}],\"payment\":{\"method\":\"CARD\","
                        + "\"token\":\"tok_approved\"}}")
                        .formatted(eventId, inventorySectorId)));

        assertEquals(400, error.getStatus().value());
        verify(repository, never()).insertOrder(org.mockito.ArgumentMatchers.any());
    }
}
