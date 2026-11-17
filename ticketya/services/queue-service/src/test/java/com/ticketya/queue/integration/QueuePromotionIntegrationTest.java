package com.ticketya.queue.integration;

import com.ticketya.queue.QueueServiceApplication;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Promoción perezosa por tandas: con batch-size=2 y batch-interval-seconds=1,
 * una tanda de 4 clientes debe promover exactamente a los 2 primeros cuando
 * la fila lleva abierta un intervalo, sin esperar esperas reales (la apertura
 * de la fila se retrasa por SQL, igual que el vencimiento en Inventory).
 */
@SpringBootTest(
        classes = QueueServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"queue.batch-size=2", "queue.batch-interval-seconds=1"})
class QueuePromotionIntegrationTest extends QueueIntegrationTestBase {

    @Test void lazyPromotionAdmitsExactlyOneBatchAfterOneInterval() {
        UUID event = UUID.randomUUID();
        publishEvent(event);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID fourth = UUID.randomUUID();
        assertThat(joinedBody(join(event, first)).path("position").asLong()).isEqualTo(1);
        assertThat(joinedBody(join(event, second)).path("position").asLong()).isEqualTo(2);
        assertThat(joinedBody(join(event, third)).path("position").asLong()).isEqualTo(3);
        assertThat(joinedBody(join(event, fourth)).path("position").asLong()).isEqualTo(4);

        // La fila pasó a estar abierta hace más de un intervalo: la próxima
        // lectura perezosa promueve sólo la primera tanda (posiciones 1 y 2).
        backdateQueueOpening(event, 1);

        ResponseEntity<String> firstPoll = poll(event, first);
        assertThat(firstPoll.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode firstBody = parseBody(firstPoll);
        assertThat(firstBody.path("status").asText()).isEqualTo("ADMITTED");
        String firstToken = firstBody.path("admissionToken").asText();
        assertThat(firstToken).isNotBlank();
        assertThat(firstBody.hasNonNull("admissionExpiresAt")).isTrue();
        assertThat(OffsetDateTime.parse(firstBody.path("admissionExpiresAt").asText()).toInstant())
                .as("la ventana de admisión recién comienza")
                .isAfter(java.time.Instant.now());
        assertThat(firstBody.path("batchNumber").asLong()).isEqualTo(1);
        assertThat(firstBody.path("peopleAhead").asLong()).isEqualTo(0);

        JsonNode secondBody = joinedBody(poll(event, second));
        assertThat(secondBody.path("status").asText()).isEqualTo("ADMITTED");
        assertThat(secondBody.path("admissionToken").asText()).isNotBlank();
        assertThat(secondBody.path("batchNumber").asLong()).isEqualTo(1);

        JsonNode thirdBody = joinedBody(poll(event, third));
        assertThat(thirdBody.path("status").asText()).isEqualTo("WAITING");
        assertThat(thirdBody.hasNonNull("admissionToken")).isFalse();
        assertThat(thirdBody.path("batchNumber").asLong()).isEqualTo(2);
        assertThat(thirdBody.path("peopleAhead").asLong()).isEqualTo(2);

        JsonNode fourthBody = joinedBody(poll(event, fourth));
        assertThat(fourthBody.path("status").asText()).isEqualTo("WAITING");
        assertThat(fourthBody.path("peopleAhead").asLong()).isEqualTo(3);

        // El token se recalcula desde admitted_at: el cliente lo recupera en cada poll.
        ResponseEntity<String> firstPollAgain = poll(event, first);
        assertThat(parseBody(firstPollAgain).path("admissionToken").asText()).isEqualTo(firstToken);
    }

    @Test void promotionRespectsThePositionOrderWithoutSkipping() {
        UUID event = UUID.randomUUID();
        publishEvent(event);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID fourth = UUID.randomUUID();
        // La posición 1 abandona: los que siguen WAITING nunca deben ser salteados.
        assertThat(joinedBody(join(event, first)).path("position").asLong()).isEqualTo(1);
        assertThat(joinedBody(join(event, second)).path("position").asLong()).isEqualTo(2);
        assertThat(joinedBody(join(event, third)).path("position").asLong()).isEqualTo(3);
        assertThat(joinedBody(join(event, fourth)).path("position").asLong()).isEqualTo(4);
        assertThat(leave(event, first).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Tras un intervalo, la tanda corriente cubre las posiciones <= 2: la
        // posición 1 ya no es WAITING (LEFT) y la 2 es promovida; 3 y 4 siguen.
        backdateQueueOpening(event, 1);

        JsonNode secondBody = joinedBody(poll(event, second));
        JsonNode thirdBody = joinedBody(poll(event, third));
        JsonNode fourthBody = joinedBody(poll(event, fourth));
        assertThat(secondBody.path("status").asText()).isEqualTo("ADMITTED");
        assertThat(thirdBody.path("status").asText()).isEqualTo("WAITING");
        assertThat(fourthBody.path("status").asText()).isEqualTo("WAITING");
        assertThat(thirdBody.path("peopleAhead").asLong()).isEqualTo(1);
    }

    @Test void thresholdIsCappedByTheTotalNumberOfEntries() {
        UUID event = UUID.randomUUID();
        publishEvent(event);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        joinedBody(join(event, first));
        joinedBody(join(event, second));

        // Muchos intervalos transcurridos: el tope es la cantidad de turnos (2).
        backdateQueueOpening(event, 600);

        assertThat(parseBody(poll(event, first)).path("status").asText()).isEqualTo("ADMITTED");
        assertThat(parseBody(poll(event, second)).path("status").asText()).isEqualTo("ADMITTED");
    }
}
