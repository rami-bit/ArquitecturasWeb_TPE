package com.ticketya.catalog.infrastructure.audit;

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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Publicador fire-and-forget de la bitácora centralizada (E2-02).
 *
 * <ul>
 *   <li>Cada evento se escribe siempre en el log local ({@value #LOGGER_NAME}),
 *       de modo que la traza no se pierde aunque Audit Service no responda.</li>
 *   <li>Si {@code AUDIT_SERVICE_URL} está configurada, el envío se hace en un
 *       pool acotado fuera del hilo de la solicitud: la operación de negocio
 *       nunca espera ni falla por la auditoría.</li>
 *   <li>Resiliencia: timeouts cortos y hasta {@code maxAttempts} intentos con
 *       espera lineal; el {@code eventId} generado aquí hace idempotente el
 *       reintento en Audit Service. Si la cola se llena, el evento se descarta
 *       con una advertencia (queda en el log local).</li>
 * </ul>
 */
public class AuditPublisher {
    public static final String LOGGER_NAME = "ticketya.audit";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);
    private static final int QUEUE_CAPACITY = 1_000;

    private final String endpoint;
    private final String serviceName;
    private final ObjectMapper mapper;
    private final RestClient http;
    private final ThreadPoolExecutor executor;
    private final int maxAttempts;
    private final Duration backoff;
    private final AtomicInteger inFlight = new AtomicInteger();

    public AuditPublisher(String baseUrl, String serviceName, ObjectMapper mapper) {
        this(baseUrl, serviceName, mapper, 3, Duration.ofMillis(200));
    }

    public AuditPublisher(String baseUrl, String serviceName, ObjectMapper mapper,
                          int maxAttempts, Duration backoff) {
        this.endpoint = baseUrl == null || baseUrl.isBlank()
                ? null : baseUrl.replaceAll("/+$", "") + "/internal/audit-events";
        this.serviceName = serviceName;
        this.mapper = mapper;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoff = backoff;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.http = RestClient.builder().requestFactory(factory).build();
        this.executor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                    Thread thread = new Thread(runnable, "audit-publisher");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Publicador que sólo escribe en el log local (pruebas o URL no configurada). */
    public static AuditPublisher localOnly(String serviceName, ObjectMapper mapper) {
        return new AuditPublisher(null, serviceName, mapper);
    }

    public boolean isRemoteEnabled() {
        return endpoint != null;
    }

    /** Registra el evento; nunca lanza excepciones al llamador. */
    public void publish(AuditEvent event) {
        try {
            Map<String, Object> payload = payload(event);
            log.info("audit service={} operation={} outcome={} resourceType={} resourceId={} actorId={} "
                            + "actorRole={} correlationId={} details={}",
                    serviceName, payload.get("operation"), payload.get("outcome"),
                    dash(event.resourceType()), dash(event.resourceId()), dash(event.actorId()),
                    dash(event.actorRole()), payload.get("correlationId"), event.details());
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
                log.warn("audit queue full; event kept only in local log eventId={}", payload.get("eventId"));
            }
        } catch (RuntimeException unexpected) {
            log.warn("audit event could not be published operation={}", event.operation(), unexpected);
        }
    }

    /** Espera a que se envíen los eventos pendientes (uso en pruebas y apagado ordenado). */
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

    private Map<String, Object> payload(AuditEvent event) {
        String correlationId = event.correlationId();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get("correlationId");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = "uncorrelated-" + UUID.randomUUID();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("correlationId", correlationId.trim());
        payload.put("service", serviceName);
        payload.put("operation", event.operation());
        payload.put("outcome", event.outcome() == null || event.outcome().isBlank() ? "UNKNOWN" : event.outcome());
        payload.put("actorId", event.actorId());
        payload.put("actorRole", event.actorRole());
        payload.put("resourceType", event.resourceType());
        payload.put("resourceId", event.resourceId());
        payload.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        payload.put("details", event.details());
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
                if (clientError.getStatusCode().value() != 429) {
                    log.warn("audit event rejected status={} eventId={} correlationId={}",
                            clientError.getStatusCode().value(), payload.get("eventId"), payload.get("correlationId"));
                    return;
                }
                pause(attempt);
            } catch (Exception failure) {
                if (attempt == maxAttempts) {
                    log.warn("audit service unavailable after {} attempts; event kept in local log eventId={} "
                                    + "correlationId={} cause={}",
                            maxAttempts, payload.get("eventId"), payload.get("correlationId"), failure.toString());
                    return;
                }
                pause(attempt);
            }
        }
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(backoff.toMillis() * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
