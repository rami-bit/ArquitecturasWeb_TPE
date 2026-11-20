package com.ticketya.purchase.infrastructure.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Circuit breaker de tres estados para dependencias externas. Sin dependencias
 * nuevas y con comportamiento determinista:
 *
 * <ul>
 *   <li>{@code CLOSED}: pasan todas las llamadas; tras {@code failureThreshold}
 *       fallas consecutivas se abre.</li>
 *   <li>{@code OPEN}: se rechazan llamadas sin invocar a la dependencia hasta que
 *       transcurre {@code openDuration}.</li>
 *   <li>{@code HALF_OPEN}: se permite una sola llamada de prueba; éxito cierra el
 *       circuito, falla lo reabre.</li>
 * </ul>
 *
 * <p>El estado se mantiene en memoria por instancia. Los contadores se exponen
 * para observabilidad en pruebas y logs.
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int failureThreshold;
    private final int halfOpenMaxCalls;
    private final Duration openDuration;
    private final Object lock = new Object();

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private int halfOpenCalls;
    private Instant openedAt;
    private long successfulCalls;
    private long failedCalls;
    private long rejectedCalls;

    public CircuitBreaker(String name, int failureThreshold, Duration openDuration) {
        this(name, failureThreshold, openDuration, 1);
    }

    public CircuitBreaker(String name, int failureThreshold, Duration openDuration,
                          int halfOpenMaxCalls) {
        this.name = name;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = openDuration == null ? Duration.ofSeconds(30) : openDuration;
        this.halfOpenMaxCalls = Math.max(1, halfOpenMaxCalls);
    }

    /**
     * Decide si la llamada puede proceder. En semiabierto reserva una única
     * llamada de prueba; el resto se rechaza con fallo rápido.
     */
    public boolean allowsCall() {
        synchronized (lock) {
            if (state == State.OPEN) {
                if (openedAt == null
                        || Duration.between(openedAt, Instant.now()).compareTo(openDuration) < 0) {
                    rejectedCalls++;
                    return false;
                }
                transition(State.HALF_OPEN, "la duración de apertura venció");
                halfOpenCalls = 0;
            }
            if (state == State.HALF_OPEN) {
                if (halfOpenCalls >= halfOpenMaxCalls) {
                    rejectedCalls++;
                    return false;
                }
                halfOpenCalls++;
            }
            return true;
        }
    }

    /** Registra una llamada exitosa y cierra el circuito si estaba abierto o semiabierto. */
    public void onSuccess() {
        synchronized (lock) {
            successfulCalls++;
            if (state != State.CLOSED) {
                transition(State.CLOSED, "llamada de prueba exitosa");
            }
            consecutiveFailures = 0;
            halfOpenCalls = 0;
            openedAt = null;
        }
    }

    /** Registra una falla y abre el circuito al alcanzar el umbral. */
    public void onFailure() {
        synchronized (lock) {
            failedCalls++;
            if (state == State.HALF_OPEN || consecutiveFailures + 1 >= failureThreshold) {
                consecutiveFailures = failureThreshold;
                transition(State.OPEN, "se alcanzó el umbral de fallas");
            } else {
                consecutiveFailures++;
            }
            halfOpenCalls = 0;
        }
    }

    private void transition(State next, String reason) {
        State previous = state;
        state = next;
        if (next == State.OPEN) {
            openedAt = Instant.now();
        }
        log.warn("circuit-breaker name={} from={} to={} reason={} failures={} rejections={}",
                name, previous, next, reason, failedCalls, rejectedCalls);
    }

    public String name() {
        return name;
    }

    public State state() {
        synchronized (lock) {
            return state;
        }
    }

    public int consecutiveFailures() {
        synchronized (lock) {
            return consecutiveFailures;
        }
    }

    public long successfulCalls() {
        synchronized (lock) {
            return successfulCalls;
        }
    }

    public long failedCalls() {
        synchronized (lock) {
            return failedCalls;
        }
    }

    public long rejectedCalls() {
        synchronized (lock) {
            return rejectedCalls;
        }
    }
}
