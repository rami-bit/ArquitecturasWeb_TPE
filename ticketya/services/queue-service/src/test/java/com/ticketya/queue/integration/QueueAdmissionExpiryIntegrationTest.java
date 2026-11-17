package com.ticketya.queue.integration;

import com.ticketya.queue.QueueServiceApplication;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vencimiento de la ventana de admisión (TTL=1s): el sweep perezoso marca
 * EXPIRED los turnos ADMITTED vencidos, validate responde 403, el poll deja de
 * encontrar el turno y la reentrada asigna una posición nueva al final.
 */
@SpringBootTest(
        classes = QueueServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"queue.admission-ttl-seconds=1"})
class QueueAdmissionExpiryIntegrationTest extends QueueIntegrationTestBase {

    @Test void expiredAdmissionIsRejectedAndAllowsReentry() {
        UUID event = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        publishEvent(event);
        joinedBody(join(event, first));
        joinedBody(join(event, second));
        backdateQueueOpening(event, 15);

        JsonNode firstEntry = joinedBody(poll(event, first));
        JsonNode secondEntry = joinedBody(poll(event, second));
        assertThat(firstEntry.path("status").asText()).isEqualTo("ADMITTED");
        assertThat(secondEntry.path("status").asText()).isEqualTo("ADMITTED");
        String firstToken = firstEntry.path("admissionToken").asText();
        String secondToken = secondEntry.path("admissionToken").asText();
        forceAdmissionExpiry(
                UUID.fromString(firstEntry.path("entryId").asText()),
                UUID.fromString(secondEntry.path("entryId").asText()));

        // El sweep del validate marca EXPIRED y el token vencido se rechaza.
        assertProblem(validate(event, first, firstToken, null),
                HttpStatus.FORBIDDEN, "admission-rejected");
        assertThat(jdbc().queryForObject(
                "SELECT status FROM queue_entries WHERE id = ?::uuid", String.class,
                firstEntry.path("entryId").asText())).isEqualTo("EXPIRED");

        // Sin turno ACTIVO: el poll ya no encuentra nada.
        assertProblem(poll(event, first), HttpStatus.NOT_FOUND, "entry-not-found");

        // El otro vencido también se rechaza.
        assertProblem(validate(event, second, secondToken, null),
                HttpStatus.FORBIDDEN, "admission-rejected");

        // Reentrada: posición nueva al final (1, 2 fueron asignadas antes).
        JsonNode rejoined = joinedBody(join(event, first));
        assertThat(rejoined.path("position").asLong()).isEqualTo(3);
        assertThat(rejoined.path("status").asText()).isEqualTo("WAITING");
        assertThat(rejoined.hasNonNull("admissionToken")).isFalse();
    }

    @Test void admissionInsideTheWindowStillValidates() {
        UUID event = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        publishEvent(event);
        joinedBody(join(event, client));
        backdateQueueOpening(event, 15);

        JsonNode entry = joinedBody(poll(event, client));
        String token = entry.path("admissionToken").asText();
        assertThat(entry.path("status").asText()).isEqualTo("ADMITTED");

        ResponseEntity<String> validation = validate(event, client, token, null);
        assertThat(validation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(validation).path("admitted").asBoolean()).isTrue();

        assertProblem(validate(event, client, token, null),
                HttpStatus.FORBIDDEN, "admission-rejected");
    }
}
