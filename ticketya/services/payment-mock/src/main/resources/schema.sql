CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    payment_method VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('APPROVED', 'REJECTED')),
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments (id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE refunds DROP CONSTRAINT IF EXISTS refunds_payment_id_key;
ALTER TABLE refunds ADD COLUMN IF NOT EXISTS amount NUMERIC(12, 2);
UPDATE refunds r SET amount = p.amount FROM payments p WHERE r.payment_id = p.id AND r.amount IS NULL;
ALTER TABLE refunds ALTER COLUMN amount SET NOT NULL;
CREATE INDEX IF NOT EXISTS ix_refunds_payment ON refunds(payment_id);
