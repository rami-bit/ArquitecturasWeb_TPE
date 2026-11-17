-- Virtual queue of a high-demand event. Positions are allocated under a per-event
-- advisory lock so several instances never hand out the same position.
CREATE TABLE IF NOT EXISTS queue_counters (
    event_id UUID PRIMARY KEY,
    next_position BIGINT NOT NULL DEFAULT 1,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS queue_entries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    client_id UUID NOT NULL,
    position BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    admitted_at TIMESTAMP WITH TIME ZONE,
    admission_expires_at TIMESTAMP WITH TIME ZONE,
    consumed_at TIMESTAMP WITH TIME ZONE,
    idempotency_key VARCHAR(120),
    CONSTRAINT uk_queue_entry_position UNIQUE (event_id, position),
    CONSTRAINT ck_queue_entry_status CHECK (status IN ('WAITING','ADMITTED','CONSUMED','EXPIRED','LEFT'))
);

-- A client may hold only one ACTIVE entry per event; closed entries (LEFT, EXPIRED,
-- CONSUMED) stay for audit and must not block a new join at the tail.
CREATE UNIQUE INDEX IF NOT EXISTS uk_queue_entry_active_client
    ON queue_entries (event_id, client_id)
    WHERE status IN ('WAITING','ADMITTED');

CREATE INDEX IF NOT EXISTS ix_queue_entries_event_status_position
    ON queue_entries (event_id, status, position);
