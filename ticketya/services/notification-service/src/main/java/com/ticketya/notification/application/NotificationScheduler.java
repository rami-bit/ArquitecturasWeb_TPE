package com.ticketya.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Ejecuta periódicamente la resolución de destinatarios y la entrega. */
@Component
@ConditionalOnProperty(name = "notifications.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final TriggerProcessor processor;
    private final DeliveryWorker worker;

    public NotificationScheduler(TriggerProcessor processor, DeliveryWorker worker) {
        this.processor = processor;
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${notifications.scheduler.delay-ms:1000}")
    public void tick() {
        try {
            processor.processDue();
            worker.deliverDue();
        } catch (RuntimeException unexpected) {
            log.warn("notification scheduler tick failed: {}", unexpected.toString());
        }
    }
}
