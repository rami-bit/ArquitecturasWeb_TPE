package com.ticketya.queue.application;

import com.ticketya.queue.api.entry.QueueEntryResponse;
import com.ticketya.queue.api.error.ApiProblemException;
import com.ticketya.queue.api.identity.Requester;
import com.ticketya.queue.api.internal.AdmissionValidationRequest;
import com.ticketya.queue.api.internal.AdmissionValidationResponse;
import com.ticketya.queue.config.QueueProperties;
import com.ticketya.queue.domain.AdmissionTokenCodec;
import com.ticketya.queue.domain.AdmissionWindow;
import com.ticketya.queue.domain.BatchCalculator;
import com.ticketya.queue.infrastructure.persistence.QueueStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Ciclo de vida de la fila virtual: join, poll, leave y validación de
 * admisiones. Todo cambio de estado corre en una transacción custodiada por
 * el advisory lock por evento; la promoción por tandas y el vencimiento de
 * tokens se aplican en forma perezosa antes de leer cualquier turno. El
 * cliente de Catalog se consulta SIEMPRE fuera de la transacción.
 */
@Service
public class QueueService {

    /** Resultado del join: el cuerpo de la respuesta y si se asignó una posición nueva. */
    public record JoinOutcome(QueueEntryResponse response, boolean created) {
        public static JoinOutcome of(QueueEntryResponse response, boolean created) {
            return new JoinOutcome(response, created);
        }
    }

    private record EntryView(QueueStore.EntryRow entry, long peopleAhead) {
    }

    /** Estado resultante de validar una admisión, resuelto fuera de la transacción. */
    private record ValidationAttempt(QueueStore.EntryRow admittedEntry, String rejectionReason) {
        static ValidationAttempt admitted(QueueStore.EntryRow entry) {
            return new ValidationAttempt(entry, null);
        }

        static ValidationAttempt rejected(String reason) {
            return new ValidationAttempt(null, reason);
        }

        boolean missing() {
            return admittedEntry == null && rejectionReason == null;
        }
    }

    private final QueueStore store;
    private final CatalogSnapshotClient catalog;
    private final TransactionTemplate transactions;
    private final QueueProperties properties;

    public QueueService(QueueStore store, CatalogSnapshotClient catalog,
                        TransactionTemplate transactions, QueueProperties properties) {
        this.store = store;
        this.catalog = catalog;
        this.transactions = transactions;
        this.properties = properties;
    }

    /**
     * Idempotente: un cliente con turno ACTIVO conserva su posición. La
     * búsqueda previa al lock evita depender de Catalog en los reintentos;
     * dentro de la transacción se re-verifica bajo el advisory lock.
     */
    public JoinOutcome join(UUID eventId, Requester requester) {
        UUID clientId = requester.id();
        if (store.findActiveEntry(eventId, clientId).isEmpty()) {
            CatalogSnapshot snapshot = catalog.fetch(eventId).orElseThrow(() ->
                    ApiProblemException.notFound(
                            "The event does not exist or does not have a published snapshot"));
            if (!snapshot.isHighDemand()) {
                throw ApiProblemException.eventNotQueued(
                        "The event is published but is not marked as high demand");
            }
        }
        return transactions.execute(status -> {
            refresh(eventId);
            Optional<EntryView> existing = readView(eventId, clientId);
            if (existing.isPresent()) {
                return JoinOutcome.of(toResponse(existing.get()), false);
            }
            store.upsertCounter(eventId);
            long position = store.readNextPositionForUpdate(eventId);
            store.insertEntry(UUID.randomUUID(), eventId, clientId, position);
            store.bumpCounter(eventId);
            EntryView created = readView(eventId, clientId)
                    .orElseThrow(() -> new IllegalStateException("queue entry vanished after insert"));
            return JoinOutcome.of(toResponse(created), true);
        });
    }

    public QueueEntryResponse poll(UUID eventId, Requester requester) {
        catalog.fetch(eventId).orElseThrow(() ->
                ApiProblemException.queueClosed("The event is no longer published"));
        EntryView view = transactions.execute(status -> {
            refresh(eventId);
            return readView(eventId, requester.id());
        }).orElseThrow(() ->
                ApiProblemException.entryNotFound("The client has no active queue entry for this event"));
        return toResponse(view);
    }

    public void leave(UUID eventId, Requester requester) {
        Integer closed = transactions.execute(status -> store.leave(eventId, requester.id()));
        if (closed == null || closed == 0) {
            throw ApiProblemException.entryNotFound(
                    "The client has no active queue entry for this event");
        }
    }

    /**
     * Valida el token en la misma transacción que marca el turno CONSUMED.
     * Los rechazos se resuelven fuera de la transacción para que el sweep de
     * vencimiento y la promoción queden persistidos también al responder 403.
     */
    public AdmissionValidationResponse validate(AdmissionValidationRequest request) {
        UUID eventId = request.eventId();
        UUID clientId = request.clientId();
        ValidationAttempt attempt = transactions.execute(status -> {
            refresh(eventId);
            Optional<QueueStore.EntryRow> row = store.findEntry(eventId, clientId);
            if (row.isEmpty()) {
                return new ValidationAttempt(null, null);
            }
            QueueStore.EntryRow candidate = row.get();
            if (!"ADMITTED".equals(candidate.status())) {
                return ValidationAttempt.rejected("The queue entry is not in an admissible state");
            }
            if (!AdmissionTokenCodec.verify(properties.getAdmissionSecret(), candidate.id(),
                    eventId, clientId, candidate.admittedAt().toInstant(), request.token())) {
                return ValidationAttempt.rejected("The admission token does not match this entry");
            }
            if (AdmissionWindow.isExpired(candidate.admissionExpiresAt(), Instant.now())) {
                return ValidationAttempt.rejected("The admission window already expired");
            }
            int consumed = store.consume(candidate.id(), request.idempotencyKey());
            if (consumed != 1) {
                return ValidationAttempt.rejected("The admission is no longer valid");
            }
            return ValidationAttempt.admitted(candidate);
        });
        if (attempt.missing()) {
            throw ApiProblemException.entryNotFound(
                    "No queue entry exists for this event and client");
        }
        if (attempt.rejectionReason() != null) {
            throw ApiProblemException.admissionRejected(attempt.rejectionReason());
        }
        return new AdmissionValidationResponse(true,
                attempt.admittedEntry().id(), attempt.admittedEntry().admissionExpiresAt());
    }

    /**
     * Reparación perezosa del estado de un evento, serializada por el advisory
     * lock: los turnos ADMITTED vencidos pasan a EXPIRED y la tanda corriente
     * de WAITING es promovida a ADMITTED.
     */
    private void refresh(UUID eventId) {
        store.lockEvent(eventId);
        store.expireDueEntries(eventId);
        store.findOpenedAt(eventId).ifPresent(openedAt -> {
            long elapsedSeconds = Math.max(0,
                    Duration.between(openedAt.toInstant(), Instant.now()).getSeconds());
            long totalEntries = store.countEntries(eventId);
            long admittedThrough = BatchCalculator.admittedThrough(elapsedSeconds,
                    properties.getBatchIntervalSeconds(), properties.getBatchSize(), totalEntries);
            if (admittedThrough > 0) {
                store.promoteThrough(eventId, admittedThrough, properties.getAdmissionTtlSeconds());
            }
        });
    }

    private Optional<EntryView> readView(UUID eventId, UUID clientId) {
        return store.findActiveEntry(eventId, clientId)
                .map(entry -> new EntryView(entry, store.countActiveAhead(eventId, entry.position())));
    }

    private QueueEntryResponse toResponse(EntryView view) {
        QueueStore.EntryRow entry = view.entry();
        String token = null;
        OffsetDateTime expiresAt = null;
        if ("ADMITTED".equals(entry.status())) {
            token = AdmissionTokenCodec.encode(properties.getAdmissionSecret(),
                    entry.id(), entry.eventId(), entry.clientId(), entry.admittedAt().toInstant());
            expiresAt = entry.admissionExpiresAt();
        }
        return new QueueEntryResponse(entry.id(), entry.eventId(), entry.position(),
                view.peopleAhead(), properties.getBatchSize(),
                BatchCalculator.batchOf(entry.position(), properties.getBatchSize()),
                entry.status(), token, expiresAt);
    }
}
