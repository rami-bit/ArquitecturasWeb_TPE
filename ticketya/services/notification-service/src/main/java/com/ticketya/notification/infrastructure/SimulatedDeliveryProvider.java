package com.ticketya.notification.infrastructure;

import com.ticketya.notification.application.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pasarela simulada de email y push, análoga a Payment Mock: no envía correos
 * reales, registra cada entrega en el log {@value #LOGGER_NAME}. Con modo
 * {@code FAILING} simula una pasarela caída para ejercitar reintentos.
 */
@Component
public class SimulatedDeliveryProvider implements DeliveryProvider {
    public static final String LOGGER_NAME = "ticketya.delivery";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private volatile boolean failing;

    public SimulatedDeliveryProvider(NotificationProperties properties) {
        this.failing = "FAILING".equalsIgnoreCase(properties.provider().mode());
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    @Override
    public void send(JdbcNotificationRepository.ClaimedNotification notification) {
        if (failing) {
            throw new IllegalStateException("Pasarela de " + notification.channel() + " no disponible (simulada)");
        }
        log.info("delivered channel={} customerId={} type={} subject=\"{}\" correlationId={}",
                notification.channel(), notification.customerId(), notification.type(), notification.subject(),
                notification.correlationId());
    }
}
