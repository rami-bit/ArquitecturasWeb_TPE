package com.ticketya.notification.infrastructure;

/** Pasarela de entrega de email/push. Lanza una excepción si el envío falla. */
public interface DeliveryProvider {
    void send(JdbcNotificationRepository.ClaimedNotification notification);
}
