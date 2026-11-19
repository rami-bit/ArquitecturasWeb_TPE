-- Bitácora centralizada de operaciones críticas (E2-02).
-- Append-only: el servicio sólo inserta y consulta; nunca actualiza ni borra.
-- La clave primaria es el eventId generado por el productor, lo que vuelve
-- idempotente el reintento de un mismo evento (ON CONFLICT DO NOTHING).
CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY,
    correlation_id VARCHAR(120) NOT NULL,
    service VARCHAR(60) NOT NULL,
    operation VARCHAR(60) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    actor_id VARCHAR(64),
    actor_role VARCHAR(20),
    resource_type VARCHAR(40),
    resource_id VARCHAR(120),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_correlation_not_blank CHECK (CHAR_LENGTH(TRIM(correlation_id)) > 0),
    CONSTRAINT ck_audit_service_not_blank CHECK (CHAR_LENGTH(TRIM(service)) > 0),
    CONSTRAINT ck_audit_operation_not_blank CHECK (CHAR_LENGTH(TRIM(operation)) > 0),
    CONSTRAINT ck_audit_outcome_not_blank CHECK (CHAR_LENGTH(TRIM(outcome)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_audit_correlation ON audit_events (correlation_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_operation ON audit_events (operation, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_resource ON audit_events (resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_occurred ON audit_events (occurred_at DESC);
