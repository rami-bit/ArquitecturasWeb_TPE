package com.ticketya.queue.integration;

import com.ticketya.queue.QueueServiceApplication;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Casos de fallo de Catálogo desde el punto de vista de la fila: evento sin
 * alta demanda (409 event-not-queued), snapshot sin el campo highDemand,
 * evento no publicado (join 404), caída de Catalog (503), fila cerrada
 * (409 queue-closed) y un leave que no depende de Catalog.
 */
@SpringBootTest(
        classes = QueueServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueCatalogFailureIntegrationTest extends QueueIntegrationTestBase {

    @Test void lowDemandEventsAreNotQueued() {
        UUID event = UUID.randomUUID();
        publishLowDemandEvent(event);
        assertProblem(join(event, UUID.randomUUID()),
                HttpStatus.CONFLICT, "event-not-queued");
        // El fallo de regla no consumió posiciones.
        publishEvent(event);
        assertThat(joinedBody(join(event, UUID.randomUUID())).path("position").asLong())
                .isEqualTo(1);
    }

    @Test void snapshotsWithoutHighDemandAreTreatedAsLowDemand() {
        UUID event = UUID.randomUUID();
        publishEventWithoutHighDemand(event);
        assertProblem(join(event, UUID.randomUUID()),
                HttpStatus.CONFLICT, "event-not-queued");
    }

    @Test void unknownOrUnpublishedEventsAreNotFoundOnJoin() {
        assertProblem(join(UUID.randomUUID(), UUID.randomUUID()),
                HttpStatus.NOT_FOUND, "about:blank");
    }

    @Test void catalogOutagesReturnServiceUnavailable() {
        failCatalog();
        try {
            assertProblem(join(UUID.randomUUID(), UUID.randomUUID()),
                    HttpStatus.SERVICE_UNAVAILABLE, "about:blank");
            assertProblem(poll(UUID.randomUUID(), UUID.randomUUID()),
                    HttpStatus.SERVICE_UNAVAILABLE, "about:blank");
        } finally {
            catalogDown = false;
        }
    }

    @Test void pollsDetectAClosedQueue() {
        UUID event = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        publishEvent(event);
        joinedBody(join(event, client));

        unpublishEvent(event);
        assertProblem(poll(event, client), HttpStatus.CONFLICT, "queue-closed");
    }

    @Test void pollsOnPublishedEventsWithoutEntryAreNotFound() {
        UUID event = UUID.randomUUID();
        publishEvent(event);
        assertProblem(poll(event, UUID.randomUUID()),
                HttpStatus.NOT_FOUND, "entry-not-found");
    }

    @Test void leavingDoesNotDependOnCatalog() {
        // El evento no tiene snapshot publicado: el leave igual responde por estado local.
        assertProblem(leave(UUID.randomUUID(), UUID.randomUUID()),
                HttpStatus.NOT_FOUND, "entry-not-found");
    }
}
