-- PDF-F11: notificaciones al cliente.
--
-- notification_triggers es la bandeja de entrada (inbox): un registro por hecho
-- de negocio recibido (compra confirmada, evento cancelado, etc.). dedupe_key
-- vuelve idempotente el reintento del productor.
--
-- notifications es la bandeja de salida (outbox): un registro por destinatario
-- y canal. Ambos procesos usan arrendamiento (next_attempt_at) y
-- FOR UPDATE SKIP LOCKED, por lo que varias instancias no procesan lo mismo.
CREATE TABLE IF NOT EXISTS notification_triggers (
    id UUID PRIMARY KEY,
    dedupe_key VARCHAR(200) NOT NULL,
    type VARCHAR(40) NOT NULL,
    payload JSONB NOT NULL,
    correlation_id VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    recipients INTEGER,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uk_notification_trigger_dedupe UNIQUE (dedupe_key),
    CONSTRAINT ck_notification_trigger_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED')),
    CONSTRAINT ck_notification_trigger_type CHECK (type IN ('ORDER_CONFIRMED', 'ORDER_CANCELLED',
        'EVENT_PUBLISHED', 'EVENT_SUSPENDED', 'EVENT_RESUMED', 'EVENT_CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_notification_triggers_due
    ON notification_triggers (next_attempt_at) WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    trigger_id UUID NOT NULL REFERENCES notification_triggers(id),
    customer_id UUID NOT NULL,
    type VARCHAR(40) NOT NULL,
    channel VARCHAR(10) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    event_id UUID,
    order_id UUID,
    correlation_id VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    CONSTRAINT uk_notification_recipient_channel UNIQUE (trigger_id, customer_id, channel),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('EMAIL', 'PUSH'))
);

CREATE INDEX IF NOT EXISTS idx_notifications_due
    ON notifications (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_notifications_customer ON notifications (customer_id, created_at DESC);
