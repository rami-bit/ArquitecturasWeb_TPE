package com.ticketya.queue.integration;

import com.ticketya.queue.QueueServiceApplication;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flujo de join/poll/leave: posiciones secuenciales, idempotencia del
 * re-join, asignación concurrente sin huecos, identidad confiable,
 * vencimiento de Catálogo y abandono con reentrada al final.
 */
@SpringBootTest(
        classes = QueueServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueJoinFlowIntegrationTest extends QueueIntegrationTestBase {

    @Test void joinCreatesWaitingEntriesWithSequentialPositions() {
        UUID event = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        publishEvent(event);

        ResponseEntity<String> firstJoin = join(event, first);
        assertThat(firstJoin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstJoin.getHeaders().getFirst(CORRELATION_HEADER)).isNotBlank();
        JsonNode firstBody = parseBody(firstJoin);
        assertThat(firstBody.path("eventId").asText()).isEqualTo(event.toString());
        String firstEntryId = firstBody.path("entryId").asText();
        assertThat(firstEntryId).isNotBlank();
        assertThat(firstBody.path("position").asLong()).isEqualTo(1);
        assertThat(firstBody.path("peopleAhead").asLong()).isEqualTo(0);
        assertThat(firstBody.path("batchSize").asInt()).isEqualTo(10);
        assertThat(firstBody.path("batchNumber").asLong()).isEqualTo(1);
        assertThat(firstBody.path("status").asText()).isEqualTo("WAITING");
        assertThat(firstBody.hasNonNull("admissionToken")).isFalse();
        assertThat(firstBody.hasNonNull("admissionExpiresAt")).isFalse();

        ResponseEntity<String> secondJoin = join(event, second);
        assertThat(secondJoin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode secondBody = parseBody(secondJoin);
        assertThat(secondBody.path("position").asLong()).isEqualTo(2);
        assertThat(secondBody.path("peopleAhead").asLong()).isEqualTo(1);

        // El poll refresca el turno sin cambiar su identidad ni posición.
        ResponseEntity<String> polled = poll(event, first);
        assertThat(polled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode pollBody = parseBody(polled);
        assertThat(pollBody.path("entryId").asText()).isEqualTo(firstEntryId);
        assertThat(pollBody.path("position").asLong()).isEqualTo(1);
        assertThat(pollBody.path("status").asText()).isEqualTo("WAITING");
    }

    @Test void repeatedJoinReturnsTheSameEntryWithoutConsumingPositions() {
        UUID event = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        publishEvent(event);

        JsonNode created = joinedBody(join(event, client));
        assertThat(created.path("position").asLong()).isEqualTo(1);

        ResponseEntity<String> repeated = join(event, client);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode repeatedBody = parseBody(repeated);
        assertThat(repeatedBody.path("entryId").asText()).isEqualTo(created.path("entryId").asText());
        assertThat(repeatedBody.path("position").asLong()).isEqualTo(1);
        assertThat(repeatedBody.path("status").asText()).isEqualTo("WAITING");

        // La posición siguiente no fue consumida por el re-join.
        JsonNode other = joinedBody(join(event, UUID.randomUUID()));
        assertThat(other.path("position").asLong()).isEqualTo(2);
    }

    @Test void concurrentJoinsAllocateUniqueSequentialPositions() throws Exception {
        UUID event = UUID.randomUUID();
        publishEvent(event);
        int clients = 8;
        List<UUID> clientIds = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            clientIds.add(UUID.randomUUID());
        }

        ExecutorService executor = Executors.newFixedThreadPool(clients);
        try {
            List<Callable<ResponseEntity<String>>> calls = new ArrayList<>();
            for (UUID clientId : clientIds) {
                calls.add(() -> join(event, clientId));
            }
            List<Future<ResponseEntity<String>>> futures = executor.invokeAll(calls);
            List<Long> positions = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                ResponseEntity<String> response = future.get(10, TimeUnit.SECONDS);
                assertThat(response.getStatusCode())
                        .as("cada cliente nuevo obtiene su propio turno")
                        .isEqualTo(HttpStatus.CREATED);
                positions.add(parseBody(response).path("position").asLong());
            }
            assertThat(positions).as("posiciones 1..8 sin repetir ni huecos")
                    .satisfiesExactlyInAnyOrder(
                            p -> assertThat(p).isEqualTo(1),
                            p -> assertThat(p).isEqualTo(2),
                            p -> assertThat(p).isEqualTo(3),
                            p -> assertThat(p).isEqualTo(4),
                            p -> assertThat(p).isEqualTo(5),
                            p -> assertThat(p).isEqualTo(6),
                            p -> assertThat(p).isEqualTo(7),
                            p -> assertThat(p).isEqualTo(8));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void identityIsDerivedFromTrustedHeadersOnly() {
        UUID event = UUID.randomUUID();
        publishEvent(event);
        String path = "/api/queue/events/" + event + "/entries";

        // Sin X-User-Id.
        assertProblem(exchange(HttpMethod.POST, path, null, identityRoleOnly("CLIENT")),
                HttpStatus.FORBIDDEN, "about:blank");
        // X-User-Id no es un UUID.
        assertProblem(joinRaw(event, "not-a-uuid", "CLIENT"),
                HttpStatus.FORBIDDEN, "about:blank");
        // Rol distinto de CLIENT/ADMIN.
        assertProblem(join(event, UUID.randomUUID(), "ORGANIZER"),
                HttpStatus.FORBIDDEN, "about:blank");
        // Rol ausente.
        assertProblem(join(event, UUID.randomUUID(), null),
                HttpStatus.FORBIDDEN, "about:blank");
        // Poll con identidad inválida.
        assertProblem(exchange(HttpMethod.GET, "/api/queue/events/" + event + "/entries/me",
                        null, identityRoleOnly("ADMIN")),
                HttpStatus.FORBIDDEN, "about:blank");
        // Rol ADMIN también puede entrar a la fila.
        assertThat(join(event, UUID.randomUUID(), "ADMIN").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    private static Map<String, String> identityRoleOnly(String role) {
        return Map.of(ROLE_HEADER, role);
    }

    @Test void catalogOutagesFailTheJoinAndThePoll() {
        UUID event = UUID.randomUUID();
        publishEvent(event);
        failCatalog();
        try {
            assertProblem(join(event, UUID.randomUUID()), HttpStatus.SERVICE_UNAVAILABLE, "about:blank");
            assertProblem(poll(event, UUID.randomUUID()), HttpStatus.SERVICE_UNAVAILABLE, "about:blank");
        } finally {
            catalogDown = false;
        }
        // Recuperado el servicio, el join funciona normalmente.
        assertThat(join(event, UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test void leavingClosesTheEntryAndReentryGoesToTheTail() {
        UUID event = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID leaver = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        publishEvent(event);
        assertThat(joinedBody(join(event, first)).path("position").asLong()).isEqualTo(1);
        assertThat(joinedBody(join(event, leaver)).path("position").asLong()).isEqualTo(2);
        assertThat(joinedBody(join(event, third)).path("position").asLong()).isEqualTo(3);

        ResponseEntity<String> leave = leave(event, leaver);
        assertThat(leave.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(leave.getBody()).as("204 sin cuerpo").isNullOrEmpty();

        assertProblem(poll(event, leaver), HttpStatus.NOT_FOUND, "entry-not-found");
        assertProblem(leave(event, leaver), HttpStatus.NOT_FOUND, "entry-not-found");

        // Reentrada: posición posterior al final, nunca se reutiliza la anterior.
        JsonNode rejoined = joinedBody(join(event, leaver));
        assertThat(rejoined.path("position").asLong()).isEqualTo(4);
        assertThat(rejoined.path("status").asText()).isEqualTo("WAITING");

        // El resto de la fila no se movió ni fue afectada.
        assertThat(poll(event, first).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parseBody(poll(event, third)).path("position").asLong()).isEqualTo(3);
        assertThat(parseBody(poll(event, first)).path("peopleAhead").asLong()).isEqualTo(0);
    }
}
