CREATE TABLE IF NOT EXISTS ticket_issuances (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ticket_issuance_order_item UNIQUE (order_id, order_item_id)
);

CREATE TABLE IF NOT EXISTS tickets (
    id UUID PRIMARY KEY,
    issuance_id UUID NOT NULL REFERENCES ticket_issuances(id) ON DELETE CASCADE,
    unit_number INTEGER NOT NULL,
    customer_id UUID NOT NULL,
    event_id UUID NOT NULL,
    event_name VARCHAR(200) NOT NULL,
    event_starts_at TIMESTAMPTZ NOT NULL,
    venue_name VARCHAR(200) NOT NULL,
    venue_city VARCHAR(120) NOT NULL,
    sector_name VARCHAR(120) NOT NULL,
    row_label VARCHAR(40),
    seat_number INTEGER,
    qr_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ISSUED',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ticket_issuance_unit UNIQUE (issuance_id, unit_number),
    CONSTRAINT uk_ticket_qr_hash UNIQUE (qr_hash),
    CONSTRAINT ck_ticket_unit_number CHECK (unit_number > 0),
    CONSTRAINT ck_ticket_name_not_blank CHECK (CHAR_LENGTH(TRIM(event_name)) > 0),
    CONSTRAINT ck_ticket_venue_not_blank CHECK (CHAR_LENGTH(TRIM(venue_name)) > 0),
    CONSTRAINT ck_ticket_city_not_blank CHECK (CHAR_LENGTH(TRIM(venue_city)) > 0),
    CONSTRAINT ck_ticket_sector_not_blank CHECK (CHAR_LENGTH(TRIM(sector_name)) > 0),
    CONSTRAINT ck_ticket_seat_pair CHECK (
        (row_label IS NULL AND seat_number IS NULL)
        OR (
            row_label IS NOT NULL
            AND CHAR_LENGTH(TRIM(row_label)) > 0
            AND seat_number IS NOT NULL
            AND seat_number > 0
        )
    ),
    CONSTRAINT ck_ticket_status CHECK (status IN ('ISSUED', 'REVOKED', 'USED'))
);

CREATE INDEX IF NOT EXISTS idx_tickets_customer_event ON tickets (customer_id, event_id);
