package com.ticketya.purchase.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketya.purchase.infrastructure.resilience.CircuitBreaker;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Despacho de notificaciones con fallback local (PDF-NF-RESILIENCE).
 *
 * <p>El evento siempre se escribe en el log local {@value #LOGGER_NAME} con su
 * {@code correlationId}. Si {@code NOTIFICATION_SERVICE_URL} está vacía no hay
 * envío remoto y ese log es la única degradez. Si hay URL, el envío se hace
 * fuera del hilo de la solicitud y se protege con el circuito: ante falla o
 * circuito abierto, el evento queda en el log local y la compra continúa.
 * Este componente nunca lanza excepciones al llamador.
 */
public class NotificationDispatcher {

    public static final String LOGGER_NAME = "ticketya.notification";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private final String endpoint;
    private final ObjectMapper mapper;
    private final CircuitBreaker breaker;
    private final RestClient http;
    private final ThreadPoolExecutor executor;

    public NotificationDispatcher(String baseUrl, ObjectMapper mapper, CircuitBreaker breaker) {
        this.endpoint = baseUrl == null || baseUrl.isBlank()
                ? null : baseUrl.replaceAll("/+$", "") + "/internal/notifications";
        this.mapper = mapper;
        this.breaker = breaker;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.http = RestClient.builder().requestFactory(factory).build();
        this.executor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200), runnable -> {
                    Thread thread = new Thread(runnable, "notification-dispatcher");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean isRemoteEnabled() {
        return endpoint != null;
    }

    /** Publica el evento; nunca lanza excepciones ni bloquea a quien llama. */
    public void publish(NotificationEvent event) {
        if (event == null) {
            return;
        }
        log.info("event={} orderId={} customerId={} correlationId={}",
                event.type(), event.orderId(), event.customerId(), event.correlationId());
        if (endpoint == null) {
            return;
        }
        if (!breaker.allowsCall()) {
            log.warn("Fallback local: circuito {} abierto, evento {} no enviado; correlationId={}",
                    breaker.name(), event.type(), event.correlationId());
            return;
        }
        try {
            executor.execute(() -> send(event));
        } catch (RejectedExecutionException rejected) {
            breaker.onFailure();
            log.warn("Fallback local: cola de notificaciones llena; correlationId={}",
                    event.correlationId());
        }
    }

    private void send(NotificationEvent event) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", event.type());
            body.put("orderId", event.orderId().toString());
            body.put("customerId", event.customerId().toString());
            body.put("correlationId", event.correlationId());
            http.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Correlation-Id", event.correlationId())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            breaker.onSuccess();
        } catch (RuntimeException exception) {
            breaker.onFailure();
            log.warn("Fallback local: adaptador de notificaciones no disponible; "
                    + "evento={} correlationId={} motivo={}",
                    event.type(), event.correlationId(), exception.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
