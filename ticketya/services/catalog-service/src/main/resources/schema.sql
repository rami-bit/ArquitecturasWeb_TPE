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
