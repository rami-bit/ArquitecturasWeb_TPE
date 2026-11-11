CREATE TABLE IF NOT EXISTS purchase_orders (
  id UUID PRIMARY KEY,
  customer_id UUID NOT NULL,
  event_id UUID NOT NULL,
  event_name VARCHAR(200) NOT NULL,
  event_starts_at TIMESTAMPTZ NOT NULL,
  venue_name VARCHAR(200) NOT NULL,
  venue_city VARCHAR(120) NOT NULL,
  currency VARCHAR(10) NOT NULL DEFAULT 'ARS',
  total_amount NUMERIC(12,2) NOT NULL,
  status VARCHAR(32) NOT NULL,
  payment_id UUID,
  payment_status VARCHAR(16),
  payment_method VARCHAR(40) NOT NULL,
  payment_created_at TIMESTAMPTZ,
  payment_updated_at TIMESTAMPTZ,
  idempotency_key VARCHAR(120) NOT NULL,
  request_fingerprint VARCHAR(128) NOT NULL,
  reservation_id UUID,
  reservation_expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT purchase_orders_customer_id_idempotency_key_key UNIQUE (customer_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS customer_event_limits (
  id UUID PRIMARY KEY,
  customer_id UUID NOT NULL,
  event_id UUID NOT NULL,
  committed_units INTEGER NOT NULL DEFAULT 0,
  pending_units INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT customer_event_limits_customer_id_event_id_key UNIQUE (customer_id, event_id)
);

CREATE TABLE IF NOT EXISTS purchase_order_items (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES purchase_orders(id),
  inventory_sector_id UUID NOT NULL,
  source_sector_id UUID NOT NULL,
  sector_name VARCHAR(120) NOT NULL,
  sector_type VARCHAR(32) NOT NULL,
  quantity INTEGER NOT NULL,
  unit_price NUMERIC(12,2) NOT NULL,
  subtotal NUMERIC(12,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS order_item_units (
  id UUID PRIMARY KEY,
  order_item_id UUID NOT NULL REFERENCES purchase_order_items(id),
  unit_number INTEGER NOT NULL,
  inventory_seat_id UUID,
  source_seat_id UUID,
  row_label VARCHAR(40),
  seat_number INTEGER,
  CONSTRAINT order_item_units_order_item_id_unit_number_key UNIQUE (order_item_id, unit_number)
);

CREATE TABLE IF NOT EXISTS order_tickets (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES purchase_orders(id),
  order_item_id UUID NOT NULL REFERENCES purchase_order_items(id),
  ticket_id UUID NOT NULL,
  unit_number INTEGER NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ISSUED',
  qr_payload TEXT,
  qr_png_base64 TEXT,
  row_label VARCHAR(40),
  seat_number INTEGER,
  CONSTRAINT order_tickets_order_id_order_item_id_unit_number_key UNIQUE (order_id, order_item_id, unit_number)
);

CREATE INDEX IF NOT EXISTS ix_purchase_orders_customer_event ON purchase_orders(customer_id, event_id, status);
CREATE INDEX IF NOT EXISTS ix_purchase_order_items_order ON purchase_order_items(order_id);
