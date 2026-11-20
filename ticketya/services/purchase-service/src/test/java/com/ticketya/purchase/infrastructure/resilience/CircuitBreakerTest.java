package com.ticketya.purchase.infrastructure.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Estados y contadores del circuito, sin red ni Spring. */
class CircuitBreakerTest {

    @Test
    void fallasConsecutivasAbrenElCircuitoYRechazanLlamadas() {
        CircuitBreaker breaker = new CircuitBreaker("payment", 3, Duration.ofSeconds(30));

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        breaker.onFailure();
        breaker.onFailure();
        assertThat(breaker.allowsCall()).isTrue();
        breaker.onFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.allowsCall()).isFalse();
        assertThat(breaker.rejectedCalls()).isEqualTo(1);
    }

    @Test
    void trasLaDuracionSePermiteUnaSolaLlamadaDePrueba() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("payment", 2, Duration.ofMillis(20));

        breaker.onFailure();
        breaker.onFailure();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(40);
        assertThat(breaker.allowsCall()).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.allowsCall()).isFalse();
        assertThat(breaker.rejectedCalls()).isEqualTo(1);

        breaker.onSuccess();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.successfulCalls()).isEqualTo(1);
        assertThat(breaker.consecutiveFailures()).isZero();
    }

    @Test
    void fallaEnSemiabiertoVuelveAabrirElCircuito() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("payment", 1, Duration.ofMillis(20));
        breaker.onFailure();
        Thread.sleep(40);

        assertThat(breaker.allowsCall()).isTrue();
        breaker.onFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.failedCalls()).isEqualTo(2);
    }

    @Test
    void exitoEnCerradoReiniciaLaRachaDeFallas() {
        CircuitBreaker breaker = new CircuitBreaker("payment", 3, Duration.ofSeconds(30));

        breaker.onFailure();
        breaker.onFailure();
        breaker.onSuccess();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.consecutiveFailures()).isZero();
    }
}
