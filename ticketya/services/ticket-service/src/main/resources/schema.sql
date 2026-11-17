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

-- E2-03 (US-F8): control de acceso. El primer escaneo válido marca la entrada
-- como USED de forma atómica (UPDATE ... WHERE status = 'ISSUED'); los
-- siguientes se rechazan. Columnas agregadas con IF NOT EXISTS para no romper
-- volúmenes existentes.
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS used_at TIMESTAMPTZ;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS used_by UUID;
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS used_gate VARCHAR(80);

-- Registro de cada intento de ingreso (aceptado o rechazado) por evento.
CREATE TABLE IF NOT EXISTS access_scans (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    ticket_id UUID,
    staff_id UUID NOT NULL,
    gate VARCHAR(80),
    result VARCHAR(20) NOT NULL,
    correlation_id VARCHAR(120),
    scanned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_access_scan_result CHECK (
        result IN ('ADMITTED', 'ALREADY_USED', 'REVOKED', 'WRONG_EVENT', 'UNKNOWN_TICKET'))
);

CREATE INDEX IF NOT EXISTS idx_access_scans_event ON access_scans (event_id, scanned_at DESC);
-- Segunda barrera contra el doble ingreso: a lo sumo un escaneo ADMITTED por entrada.
CREATE UNIQUE INDEX IF NOT EXISTS ux_access_scans_single_admission
    ON access_scans (ticket_id) WHERE result = 'ADMITTED';
