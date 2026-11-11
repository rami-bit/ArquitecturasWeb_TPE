package com.ticketya.purchase.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Beans de los clientes sincrónicos de los servicios internos (Catalog,
 * Inventory, Payment y Ticket), con las URLs resueltas desde variables de
 * entorno.
 */
@Configuration
public class ClientConfig {

    @Bean
    public CatalogClient catalogClient(
            @Value("${purchase.catalog.url:http://localhost:8081}") String baseUrl,
            ObjectMapper mapper) {
        return new CatalogClient(baseUrl, mapper);
    }

    @Bean
    public InventoryClient inventoryClient(
            @Value("${purchase.inventory.url:http://localhost:8082}") String baseUrl,
            ObjectMapper mapper) {
        return new InventoryClient(baseUrl, mapper);
    }

    @Bean
    public PaymentClient paymentClient(
            @Value("${purchase.payment.url:http://localhost:8085}") String baseUrl,
            ObjectMapper mapper) {
        return new PaymentClient(baseUrl, mapper);
    }

    @Bean
    public TicketClient ticketClient(
            @Value("${purchase.ticket.url:http://localhost:8083}") String baseUrl,
            ObjectMapper mapper) {
        return new TicketClient(baseUrl, mapper);
    }
}
