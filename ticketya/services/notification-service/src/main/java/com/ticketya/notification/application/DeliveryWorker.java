package com.ticketya.notification.application;

import com.ticketya.notification.infrastructure.DeliveryProvider;
import com.ticketya.notification.infrastructure.MissingContactException;
import com.ticketya.notification.infrastructure.JdbcNotificationRepository;
import com.ticketya.notification.infrastructure.JdbcNotificationRepository.ClaimedNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Entrega las notificaciones pendientes por la pasarela de email/push.
 * Resiliencia (PDF-NF-RESILIENCE): cada falla reprograma el envío con espera
 * exponencial; agotados los intentos queda {@code FAILED} con el último error.
 * La compra o la cancelación que originó la notificación nunca se ven afectadas.
 */
@Service
public class DeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final JdbcNotificationRepository repository;
    private final DeliveryProvider provider;
    private final NotificationProperties properties;

    public DeliveryWorker(JdbcNotificationRepository repository, DeliveryProvider provider,
                          NotificationProperties properties) {
        this.repository = repository;
        this.provider = provider;
        this.properties = properties;
    }

    /** Envía las notificaciones vencidas; devuelve cuántas reclamó. */
    public int deliverDue() {
        List<ClaimedNotification> claimed = repository.claimNotifications(properties.scheduler().batchSize(),
                properties.retry().leaseSeconds());
        for (ClaimedNotification notification : claimed) {
            try {
                provider.send(notification);
                repository.markSent(notification.id());
            } catch (MissingContactException missing) {
                repository.markSkippedNoContact(notification.id());
            } catch (RuntimeException failure) {
                if (notification.attempts() >= properties.retry().maxAttempts()) {
                    repository.failNotification(notification.id(), failure.getMessage());
                    log.warn("notification FAILED id={} channel={} attempts={} correlationId={} cause={}",
                            notification.id(), notification.channel(), notification.attempts(),
                            notification.correlationId(), failure.getMessage());
                } else {
                    long delay = RetryPolicy.delayMs(notification.attempts(), properties.retry().baseDelayMs(),
                            properties.retry().maxDelayMs());
                    repository.retryNotification(notification.id(), delay, failure.getMessage());
                    log.warn("notification retry id={} channel={} attempt={} nextInMs={} correlationId={}",
                            notification.id(), notification.channel(), notification.attempts(), delay,
                            notification.correlationId());
                }
            }
        }
        return claimed.size();
    }
}
