CREATE TABLE IF NOT EXISTS event_inventories (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    snapshot_id UUID NOT NULL,
    hold_ttl_seconds INTEGER NOT NULL DEFAULT 600,
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_event_inventory_event UNIQUE (event_id),
    CONSTRAINT ck_event_inventory_hold_ttl CHECK (hold_ttl_seconds BETWEEN 60 AND 3600)
);

CREATE TABLE IF NOT EXISTS inventory_sectors (
    id UUID PRIMARY KEY,
    event_inventory_id UUID NOT NULL REFERENCES event_inventories (id) ON DELETE CASCADE,
    source_sector_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(32) NOT NULL,
    capacity INTEGER NOT NULL,
    sold INTEGER NOT NULL DEFAULT 0,
    reserved INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_inventory_sector_source UNIQUE (event_inventory_id, source_sector_id),
    CONSTRAINT ck_inventory_sector_type CHECK (type IN ('NUMBERED', 'GENERAL_ADMISSION')),
    CONSTRAINT ck_inventory_sector_capacity CHECK (capacity > 0),
    CONSTRAINT ck_inventory_sector_sold CHECK (sold >= 0),
    CONSTRAINT ck_inventory_sector_reserved CHECK (reserved >= 0),
    CONSTRAINT ck_inventory_sector_available CHECK (sold + reserved <= capacity)
);

CREATE TABLE IF NOT EXISTS reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    event_inventory_id UUID NOT NULL REFERENCES event_inventories (id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ NULL,
    released_at TIMESTAMPTZ NULL,
    expired_at TIMESTAMPTZ NULL,
    CONSTRAINT uk_reservation_order UNIQUE (order_id),
    CONSTRAINT ck_reservation_status CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED'))
);

CREATE TABLE IF NOT EXISTS inventory_items (
    id UUID PRIMARY KEY,
    event_inventory_id UUID NOT NULL REFERENCES event_inventories (id) ON DELETE CASCADE,
    sector_id UUID NOT NULL REFERENCES inventory_sectors (id) ON DELETE CASCADE,
    source_seat_id UUID NOT NULL,
    row_label VARCHAR(40) NOT NULL,
    seat_number INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    reservation_id UUID NULL REFERENCES reservations (id) ON DELETE SET NULL,
    expires_at TIMESTAMPTZ NULL,
    sold_at TIMESTAMPTZ NULL,
    CONSTRAINT uk_inventory_item_source UNIQUE (event_inventory_id, source_seat_id),
    CONSTRAINT uk_inventory_item_position UNIQUE (sector_id, row_label, seat_number),
    CONSTRAINT ck_inventory_item_status CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD')),
    CONSTRAINT ck_inventory_item_seat_number CHECK (seat_number > 0)
);

CREATE TABLE IF NOT EXISTS reservation_items (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations (id) ON DELETE CASCADE,
    sector_id UUID NOT NULL REFERENCES inventory_sectors (id),
    inventory_item_id UUID NULL REFERENCES inventory_items (id) ON DELETE SET NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT ck_reservation_item_quantity CHECK (quantity > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_reservation_item_seat
    ON reservation_items (reservation_id, inventory_item_id)
    WHERE inventory_item_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_reservation_item_sector
    ON reservation_items (reservation_id, sector_id)
    WHERE inventory_item_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_reservations_status_expires
    ON reservations (status, expires_at);

CREATE INDEX IF NOT EXISTS idx_reservations_event_inventory
    ON reservations (event_inventory_id);

CREATE INDEX IF NOT EXISTS idx_inventory_items_status_expires
    ON inventory_items (status, expires_at);

CREATE INDEX IF NOT EXISTS idx_inventory_items_reservation
    ON inventory_items (reservation_id);

ALTER TABLE event_inventories ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS sold_unit_releases (
    reservation_item_id UUID NOT NULL REFERENCES reservation_items (id),
    unit_number INTEGER NOT NULL CHECK (unit_number > 0),
    released_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (reservation_item_id, unit_number)
);
