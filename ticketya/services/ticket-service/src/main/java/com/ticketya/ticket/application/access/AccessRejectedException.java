package com.ticketya.ticket.application.access;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Ingreso rechazado; se traduce a problem+json con {@code reason}. */
public class AccessRejectedException extends RuntimeException {
    private final AccessResult reason;
    private final UUID ticketId;
    private final OffsetDateTime usedAt;
    private final String usedGate;

    public AccessRejectedException(AccessResult reason, UUID ticketId, OffsetDateTime usedAt, String usedGate) {
        super(reason.description());
        this.reason = reason;
        this.ticketId = ticketId;
        this.usedAt = usedAt;
        this.usedGate = usedGate;
    }

    public AccessResult reason() {
        return reason;
    }

    public UUID ticketId() {
        return ticketId;
    }

    public OffsetDateTime usedAt() {
        return usedAt;
    }

    public String usedGate() {
        return usedGate;
    }
}
