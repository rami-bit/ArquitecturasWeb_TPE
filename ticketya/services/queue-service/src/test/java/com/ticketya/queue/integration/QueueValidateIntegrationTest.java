package com.ticketya.queue.integration;

import com.ticketya.queue.QueueServiceApplication;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API interna de admisiones: token propio válido consume el turno (una sola
 * vez), el token de otro cliente se rechaza, un turno WAITING no es admisible,
 * una entrada inexistente responde 404 y el request se valida en el cuerpo.
 */
@SpringBootTest(
        classes = QueueServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueValidateIntegrationTest extends QueueIntegrationTestBase {

    @Test void validateWithOwnTokenConsumesTheEntryExactlyOnce() {
        UUID event = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        publishEvent(event);
        joinedBody(join(event, client));
        backdateQueueOpening(event, 15);

        JsonNode entry = joinedBody(poll(event, client));
        assertThat(entry.path("status").asText()).isEqualTo("ADMITTED");
        String token = entry.path("admissionToken").asText();
        String entryId = entry.path("entryId").asText();

        ResponseEntity<String> validation = validate(event, client, token, "order-42");
        assertThat(validation.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = parseBody(validation);
        assertThat(body.path("admitted").asBoolean()).isTrue();
        assertThat(body.path("admissionId").asText()).isEqualTo(entryId);
        assertThat(body.path("expiresAt").asText()).isNotBlank();

        Map<String, Object> row = jdbc().queryForMap(
                "SELECT status, idempotency_key FROM queue_entries WHERE id = ?::uuid", entryId);
        assertThat(row.get("status")).isEqualTo("CONSUMED");
        assertThat(row.get("idempotency_key")).isEqualTo("order-42");

        // Segunda llamada: el turno ya está CONSUMED.
        assertProblem(validate(event, client, token, "order-42"),
                HttpStatus.FORBIDDEN, "admission-rejected");
    }

    @Test void validateRejectsATokenIssuedToAnotherClient() {
        UUID event = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        publishEvent(event);
        joinedBody(join(event, owner));
        joinedBody(join(event, attacker));
        backdateQueueOpening(event, 15);

        String ownerToken = joinedBody(poll(event, owner)).path("admissionToken").asText();
        assertThat(ownerToken).isNotBlank();

        // El atacante presenta su propio clientId con el token ajeno.
        assertProblem(validate(event, attacker, ownerToken, null),
                HttpStatus.FORBIDDEN, "admission-rejected");

        // El intento fallido no alteró el turno del atacante, que admite con su token.
        String attackerEntryId = joinedBody(poll(event, attacker)).path("entryId").asText();
        assertThat(validate(event, attacker,
                joinedBody(poll(event, attacker)).path("admissionToken").asText(), null)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc().queryForObject(
                "SELECT status FROM queue_entries WHERE id = ?::uuid",
                String.class, attackerEntryId)).isEqualTo("CONSUMED");
    }

    @Test void validateRejectsWaitingEntries() {
        UUID event = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        publishEvent(event);
        joinedBody(join(event, client));

        // Sin promoción: el turno sigue WAITING y no es admisible.
        assertProblem(validate(event, client, "any-token", null),
                HttpStatus.FORBIDDEN, "admission-rejected");
    }

    @Test void validateWithoutAnyEntryIsNotFound() {
        assertProblem(validate(UUID.randomUUID(), UUID.randomUUID(), "whatever", null),
                HttpStatus.NOT_FOUND, "entry-not-found");
    }

    @Test void validateRequiresTheMandatoryFields() {
        UUID event = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        publishEvent(event);

        // Sin token.
        String missingToken = "{\"eventId\":\"%s\",\"clientId\":\"%s\"}"
                .formatted(event, client);
        assertProblem(exchange(HttpMethod.POST, "/internal/admissions/validate",
                missingToken, Map.of()), HttpStatus.BAD_REQUEST, "about:blank");

        // Sin eventId.
        String missingEvent = "{\"clientId\":\"%s\",\"token\":\"t\"}".formatted(client);
        assertProblem(exchange(HttpMethod.POST, "/internal/admissions/validate",
                missingEvent, Map.of()), HttpStatus.BAD_REQUEST, "about:blank");

        // Cuerpo no JSON.
        assertProblem(exchange(HttpMethod.POST, "/internal/admissions/validate",
                "not-json", Map.of()), HttpStatus.BAD_REQUEST, "about:blank");
    }
}
