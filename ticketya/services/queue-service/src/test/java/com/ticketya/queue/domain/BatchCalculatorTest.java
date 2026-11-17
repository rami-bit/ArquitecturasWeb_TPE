package com.ticketya.queue.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cálculo puro de la tanda admitida: 0 segundos, un intervalo, varios
 * intervalos y el tope por cantidad de turnos del evento.
 */
class BatchCalculatorTest {

    @Test void zeroElapsedAdmitsNobody() {
        assertThat(BatchCalculator.admittedThrough(0, 15, 10, 100)).isZero();
    }

    @Test void lessThanOneIntervalStillAdmitsNobody() {
        assertThat(BatchCalculator.admittedThrough(14, 15, 10, 100)).isZero();
    }

    @Test void oneIntervalAdmitsTheFirstBatch() {
        assertThat(BatchCalculator.admittedThrough(15, 15, 10, 100)).isEqualTo(10);
    }

    @Test void severalIntervalsAdmitSeveralBatches() {
        assertThat(BatchCalculator.admittedThrough(45, 15, 10, 100)).isEqualTo(30);
        assertThat(BatchCalculator.admittedThrough(44, 15, 10, 100)).isEqualTo(20);
        assertThat(BatchCalculator.admittedThrough(46, 15, 10, 100)).isEqualTo(30);
    }

    @Test void thresholdIsCappedByTheTotalNumberOfEntries() {
        assertThat(BatchCalculator.admittedThrough(1_000, 15, 10, 7)).isEqualTo(7);
        assertThat(BatchCalculator.admittedThrough(10_000, 1, 2, 0)).isZero();
        assertThat(BatchCalculator.admittedThrough(600, 15, 10, 25)).isEqualTo(25);
    }

    @Test void negativeElapsedIsTreatedAsZero() {
        assertThat(BatchCalculator.admittedThrough(-5, 15, 10, 100)).isZero();
    }

    @Test void nonPositiveIntervalOrBatchIsRejected() {
        assertThatThrownBy(() -> BatchCalculator.admittedThrough(10, 0, 10, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BatchCalculator.admittedThrough(10, 15, 0, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void batchNumberFollowsThePosition() {
        assertThat(BatchCalculator.batchOf(1, 10)).isEqualTo(1);
        assertThat(BatchCalculator.batchOf(10, 10)).isEqualTo(1);
        assertThat(BatchCalculator.batchOf(11, 10)).isEqualTo(2);
        assertThat(BatchCalculator.batchOf(20, 10)).isEqualTo(2);
        assertThat(BatchCalculator.batchOf(3, 2)).isEqualTo(2);
        assertThat(BatchCalculator.batchOf(4, 2)).isEqualTo(2);
    }
}
