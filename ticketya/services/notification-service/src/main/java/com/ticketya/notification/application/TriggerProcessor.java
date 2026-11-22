package com.ticketya.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketya.notification.infrastructure.JdbcNotificationRepository;
import com.ticketya.notification.infrastructure.JdbcNotificationRepository.ClaimedTrigger;
import com.ticketya.notification.infrastructure.RecipientDirectoryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resuelve los destinatarios de cada disparador pendiente y crea una
 * notificación por destinatario y canal. Si Purchase o Preferences no
 * responden, el disparador se reintenta con espera exponencial y, agotados los
 * intentos, queda {@code FAILED} (visible para diagnóstico).
 */
@Service
public class TriggerProcessor {
    private static final Logger log = LoggerFactory.getLogger(TriggerProcessor.class);

    private final JdbcNotificationRepository repository;
    private final RecipientDirectoryClient directory;
    private final TransactionTemplate transactions;
    private final NotificationProperties properties;

    public TriggerProcessor(JdbcNotificationRepository repository, RecipientDirectoryClient directory,
                            TransactionTemplate transactions, NotificationProperties properties) {
        this.repository = repository;
        this.directory = directory;
        this.transactions = transactions;
        this.properties = properties;
    }

    /** Procesa los disparadores vencidos; devuelve cuántos reclamó. */
    public int processDue() {
        List<ClaimedTrigger> claimed = repository.claimTriggers(properties.scheduler().batchSize(),
                properties.retry().leaseSeconds());
        for (ClaimedTrigger trigger : claimed) {
            process(trigger);
        }
        return claimed.size();
    }

    private void process(ClaimedTrigger trigger) {
        try {
            Set<UUID> recipients = new LinkedHashSet<>(recipients(trigger));
            MessageTemplates.Message message = MessageTemplates.render(trigger.type(), trigger.payload());
            UUID eventId = uuid(trigger.payload(), "eventId");
            UUID orderId = uuid(trigger.payload(), "orderId");
            transactions.executeWithoutResult(status -> {
                for (UUID customer : recipients) {
                    for (String channel : properties.channels()) {
                        repository.insertNotification(trigger.id(), customer, trigger.type(), channel.trim(),
                                message.subject(), message.body(), eventId, orderId, trigger.correlationId());
                    }
                }
                repository.completeTrigger(trigger.id(), recipients.size());
            });
            log.info("trigger processed type={} triggerId={} recipients={} correlationId={}",
                    trigger.type(), trigger.id(), recipients.size(), trigger.correlationId());
        } catch (RuntimeException failure) {
            if (trigger.attempts() >= properties.retry().maxAttempts()) {
                repository.failTrigger(trigger.id(), failure.getMessage());
                log.warn("trigger FAILED type={} triggerId={} attempts={} correlationId={} cause={}",
                        trigger.type(), trigger.id(), trigger.attempts(), trigger.correlationId(),
                        failure.getMessage());
            } else {
                long delay = RetryPolicy.delayMs(trigger.attempts(), properties.retry().baseDelayMs(),
                        properties.retry().maxDelayMs());
                repository.retryTrigger(trigger.id(), delay, failure.getMessage());
                log.warn("trigger retry type={} triggerId={} attempt={} nextInMs={} correlationId={} cause={}",
                        trigger.type(), trigger.id(), trigger.attempts(), delay, trigger.correlationId(),
                        failure.getMessage());
            }
        }
    }

    private List<UUID> recipients(ClaimedTrigger trigger) {
        JsonNode payload = trigger.payload();
        return switch (trigger.type()) {
            case ORDER_CONFIRMED, ORDER_CANCELLED -> List.of(UUID.fromString(payload.path("customerId").asText()));
            case EVENT_PUBLISHED -> directory.fansInCity(payload.path("city").asText(null),
                    MessageTemplates.participants(payload), trigger.correlationId());
            case EVENT_SUSPENDED, EVENT_RESUMED, EVENT_CANCELLED ->
                    directory.buyersOf(UUID.fromString(payload.path("eventId").asText()), trigger.correlationId());
        };
    }

    private static UUID uuid(JsonNode payload, String field) {
        String value = payload.path(field).asText(null);
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
