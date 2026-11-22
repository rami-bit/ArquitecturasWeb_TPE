package com.ticketya.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Notification Service (PDF-F11): recibe de forma asíncrona los hechos de
 * negocio de Purchase y Catalog, resuelve los destinatarios (compradores del
 * evento o clientes con el artista favorito en su ciudad) y entrega cada
 * notificación por email y push simulados, con reintentos y estado auditable.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
