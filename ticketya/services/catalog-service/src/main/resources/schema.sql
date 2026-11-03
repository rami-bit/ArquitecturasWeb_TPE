-- Catalog Service owns this schema. Keep this bootstrap idempotent: Spring Boot
-- executes it before Hibernate validates the mappings on every application start.
-- Domain tables and indexes are added here as their corresponding tasks land.
CREATE SCHEMA IF NOT EXISTS public;

CREATE TABLE IF NOT EXISTS venues (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(300) NOT NULL,
    city VARCHAR(120) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    CONSTRAINT ck_venue_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_venue_address_not_blank CHECK (CHAR_LENGTH(TRIM(address)) > 0),
    CONSTRAINT ck_venue_city_not_blank CHECK (CHAR_LENGTH(TRIM(city)) > 0),
    CONSTRAINT ck_venue_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_venue_longitude CHECK (longitude BETWEEN -180 AND 180)
);

CREATE TABLE IF NOT EXISTS venue_sectors (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    sector_type VARCHAR(32) NOT NULL,
    general_capacity INTEGER,
    CONSTRAINT fk_venue_sector_venue FOREIGN KEY (venue_id)
        REFERENCES venues (id) ON DELETE CASCADE,
    CONSTRAINT ck_venue_sector_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_venue_sector_type CHECK (sector_type IN ('NUMBERED', 'GENERAL_ADMISSION')),
    CONSTRAINT ck_venue_sector_capacity CHECK (
        (sector_type = 'NUMBERED' AND general_capacity IS NULL)
        OR (sector_type = 'GENERAL_ADMISSION' AND general_capacity > 0)
    )
);

CREATE TABLE IF NOT EXISTS venue_seats (
    id UUID PRIMARY KEY,
    venue_sector_id UUID NOT NULL,
    row_label VARCHAR(40) NOT NULL,
    seat_number INTEGER NOT NULL,
    CONSTRAINT fk_venue_seat_sector FOREIGN KEY (venue_sector_id)
        REFERENCES venue_sectors (id) ON DELETE CASCADE,
    CONSTRAINT ck_venue_seat_row_not_blank CHECK (CHAR_LENGTH(TRIM(row_label)) > 0),
    CONSTRAINT ck_venue_seat_number CHECK (seat_number > 0),
    CONSTRAINT uk_venue_seat_position UNIQUE (venue_sector_id, row_label, seat_number)
);

CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    organizer_id UUID NOT NULL,
    venue_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT fk_event_venue FOREIGN KEY (venue_id) REFERENCES venues(id),
    CONSTRAINT ck_event_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_event_description CHECK (CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT ck_event_type CHECK (CHAR_LENGTH(TRIM(event_type)) > 0),
    CONSTRAINT ck_event_status CHECK (status IN ('DRAFT','PUBLISHED','SUSPENDED','SOLD_OUT','CANCELLED','FINISHED'))
);

CREATE TABLE IF NOT EXISTS event_participants (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    CONSTRAINT fk_event_participant_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT ck_event_participant_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT uk_event_participant UNIQUE (event_id, name)
);

CREATE TABLE IF NOT EXISTS event_sector_offerings (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    venue_sector_id UUID NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    CONSTRAINT fk_offering_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_offering_sector FOREIGN KEY (venue_sector_id) REFERENCES venue_sectors(id),
    CONSTRAINT ck_offering_price CHECK (price > 0),
    CONSTRAINT uk_event_offering UNIQUE (event_id, venue_sector_id)
);

-- Immutable, normalized publication copy. The unique event_id makes publish idempotent.
CREATE TABLE IF NOT EXISTS event_snapshots (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    venue_name VARCHAR(200) NOT NULL,
    venue_address VARCHAR(300) NOT NULL,
    venue_city VARCHAR(120) NOT NULL,
    venue_latitude NUMERIC(9,6) NOT NULL,
    venue_longitude NUMERIC(9,6) NOT NULL,
    CONSTRAINT fk_snapshot_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT uk_event_snapshot_event UNIQUE (event_id)
);
CREATE TABLE IF NOT EXISTS snapshot_participants (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES event_snapshots(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL
);
CREATE TABLE IF NOT EXISTS snapshot_sectors (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES event_snapshots(id) ON DELETE CASCADE,
    source_sector_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    sector_type VARCHAR(32) NOT NULL CHECK (sector_type IN ('NUMBERED','GENERAL_ADMISSION')),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    price NUMERIC(12,2) NOT NULL CHECK (price > 0)
);
CREATE TABLE IF NOT EXISTS snapshot_seats (
    id UUID PRIMARY KEY,
    sector_id UUID NOT NULL REFERENCES snapshot_sectors(id) ON DELETE CASCADE,
    source_seat_id UUID NOT NULL,
    row_label VARCHAR(40) NOT NULL,
    seat_number INTEGER NOT NULL CHECK (seat_number > 0),
    CONSTRAINT uk_snapshot_seat_position UNIQUE (sector_id,row_label,seat_number)
);

-- Public discovery access paths; keep these aligned with the supported filters.
CREATE INDEX IF NOT EXISTS ix_events_status_starts_at_id ON events (status, starts_at, id);
CREATE INDEX IF NOT EXISTS ix_events_status_type_starts_at_id ON events (status, event_type, starts_at, id);
CREATE INDEX IF NOT EXISTS ix_events_status_organizer_starts_at_id ON events (status, organizer_id, starts_at, id);
CREATE INDEX IF NOT EXISTS ix_event_snapshots_city ON event_snapshots (venue_city, event_id);
CREATE INDEX IF NOT EXISTS ix_snapshot_participants_snapshot_name ON snapshot_participants (snapshot_id, name);
