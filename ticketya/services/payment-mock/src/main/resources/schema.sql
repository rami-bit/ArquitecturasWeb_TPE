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
    payment_id UUID NOT NULL UNIQUE REFERENCES payments (id),
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    request_fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
