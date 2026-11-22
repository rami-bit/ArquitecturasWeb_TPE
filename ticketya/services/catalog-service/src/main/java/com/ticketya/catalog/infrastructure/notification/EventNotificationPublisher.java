package com.ticketya.catalog.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Publicador fire-and-forget de cambios de evento hacia Notification Service
 * (PDF-F11), con el mismo esquema de resiliencia que la bitácora: log local
 * siempre, envío fuera del hilo de la solicitud, timeouts cortos, hasta
 * {@code maxAttempts} intentos y descarte con advertencia si la cola se llena.
 * Publicar, suspender, reanudar o cancelar nunca fallan por las notificaciones.
 */
public class EventNotificationPublisher {
    public static final String LOGGER_NAME = "ticketya.notification";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private final String endpoint;
    private final ObjectMapper mapper;
    private final RestClient http;
    private final ThreadPoolExecutor executor;
    private final int maxAttempts;
    private final Duration backoff;
    private final AtomicInteger inFlight = new AtomicInteger();

    public EventNotificationPublisher(String baseUrl, ObjectMapper mapper) {
        this(baseUrl, mapper, 3, Duration.ofMillis(200));
    }

    public EventNotificationPublisher(String baseUrl, ObjectMapper mapper, int maxAttempts, Duration backoff) {
        this.endpoint = baseUrl == null || baseUrl.isBlank()
                ? null : baseUrl.replaceAll("/+$", "") + "/internal/notifications/event-changes";
        this.mapper = mapper;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoff = backoff;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.http = RestClient.builder().requestFactory(factory).build();
        this.executor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
                runnable -> {
                    Thread thread = new Thread(runnable, "event-notification-publisher");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean isRemoteEnabled() {
        return endpoint != null;
    }

    /** Registra y envía el cambio; nunca lanza excepciones al llamador. */
    public void publish(EventChange change) {
        try {
            Map<String, Object> payload = payload(change);
            log.info("event-change type={} eventId={} changeId={} city={} participants={} correlationId={}",
                    change.type(), change.eventId(), change.changeId(), change.city(), change.participants(),
                    payload.get("correlationId"));
            if (endpoint == null) {
                return;
            }
            inFlight.incrementAndGet();
            try {
                executor.execute(() -> {
                    try {
                        send(payload);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
            } catch (RejectedExecutionException rejected) {
                inFlight.decrementAndGet();
                log.warn("notification queue full; change kept only in local log eventId={} correlationId={}",
                        change.eventId(), payload.get("correlationId"));
            }
        } catch (RuntimeException unexpected) {
            log.warn("event change could not be published type={}", change.type(), unexpected);
        }
    }

    /** Espera a que se envíen los cambios pendientes (pruebas y apagado ordenado). */
    public boolean awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (inFlight.get() > 0) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    @PreDestroy
    public void shutdown() {
        awaitIdle(Duration.ofSeconds(2));
        executor.shutdownNow();
    }

    private Map<String, Object> payload(EventChange change) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = "uncorrelated-" + UUID.randomUUID();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", change.type());
        payload.put("eventId", change.eventId().toString());
        payload.put("changeId", change.changeId().toString());
        payload.put("eventName", change.eventName());
        payload.put("startsAt", change.startsAt() == null ? null : change.startsAt().toString());
        if (change.previousStartsAt() != null) {
            payload.put("previousStartsAt", change.previousStartsAt().toString());
        }
        payload.put("city", change.city());
        payload.put("participants", change.participants());
        payload.put("correlationId", correlationId);
        return payload;
    }

    private void send(Map<String, Object> payload) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                http.post().uri(endpoint)
                        .header("X-Correlation-Id", String.valueOf(payload.get("correlationId")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mapper.writeValueAsString(payload))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (HttpClientErrorException clientError) {
                log.warn("event change rejected status={} eventId={} correlationId={}",
                        clientError.getStatusCode().value(), payload.get("eventId"), payload.get("correlationId"));
                return;
            } catch (Exception failure) {
                if (attempt == maxAttempts) {
                    log.warn("notification service unavailable after {} attempts; change kept in local log "
                                    + "eventId={} correlationId={} cause={}", maxAttempts, payload.get("eventId"),
                            payload.get("correlationId"), failure.toString());
                    return;
                }
                try {
                    Thread.sleep(backoff.toMillis() * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
