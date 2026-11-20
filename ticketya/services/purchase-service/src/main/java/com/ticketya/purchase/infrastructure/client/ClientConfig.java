package com.ticketya.purchase.infrastructure.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.purchase.infrastructure.notification.NotificationDispatcher;
import com.ticketya.purchase.infrastructure.resilience.CircuitBreaker;

import java.time.Duration;

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
    public CircuitBreaker paymentCircuitBreaker(
            @Value("${purchase.circuit.failure-threshold:5}") int failureThreshold,
            @Value("${purchase.circuit.open-seconds:30}") long openSeconds) {
        return new CircuitBreaker("payment", failureThreshold, Duration.ofSeconds(openSeconds));
    }

    @Bean
    public CircuitBreaker notificationCircuitBreaker(
            @Value("${purchase.circuit.failure-threshold:5}") int failureThreshold,
            @Value("${purchase.circuit.open-seconds:30}") long openSeconds) {
        return new CircuitBreaker("notifications", failureThreshold, Duration.ofSeconds(openSeconds));
    }

    @Bean
    public PaymentClient paymentClient(
            @Value("${purchase.payment.url:http://localhost:8085}") String baseUrl,
            ObjectMapper mapper, @Qualifier("paymentCircuitBreaker") CircuitBreaker paymentCircuitBreaker) {
        return new PaymentClient(baseUrl, mapper, paymentCircuitBreaker);
    }

    @Bean
    public NotificationDispatcher notificationDispatcher(
            @Value("${ticketya.notifications.url:}") String baseUrl,
            ObjectMapper mapper, @Qualifier("notificationCircuitBreaker") CircuitBreaker notificationCircuitBreaker) {
        return new NotificationDispatcher(baseUrl, mapper, notificationCircuitBreaker);
    }

    @Bean
    public TicketClient ticketClient(
            @Value("${purchase.ticket.url:http://localhost:8083}") String baseUrl,
            ObjectMapper mapper) {
        return new TicketClient(baseUrl, mapper);
    }

    @Bean
    public QueueClient queueClient(
            @Value("${purchase.queue.url:http://localhost:8086}") String baseUrl,
            ObjectMapper mapper) {
        return new QueueClient(baseUrl, mapper);
    }
}
