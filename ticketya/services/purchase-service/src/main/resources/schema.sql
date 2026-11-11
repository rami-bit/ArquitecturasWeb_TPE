CREATE TABLE IF NOT EXISTS purchase_orders (
  id UUID PRIMARY KEY,
  customer_id UUID NOT NULL,
  event_id UUID NOT NULL,
  event_name VARCHAR(200) NOT NULL,
  event_starts_at TIMESTAMPTZ NOT NULL,
  venue_name VARCHAR(200) NOT NULL,
  venue_city VARCHAR(120) NOT NULL,
  payment_method VARCHAR(40) NOT NULL,
  payment_outcome VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
  status VARCHAR(32) NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL UNIQUE,
  request_fingerprint VARCHAR(64) NOT NULL,
  payment_id UUID,
  total NUMERIC(12,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_purchase_customer_event ON purchase_orders(customer_id,event_id,status);
CREATE TABLE IF NOT EXISTS purchase_order_items (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES purchase_orders(id),
  sector_id UUID NOT NULL,
  sector_name VARCHAR(120) NOT NULL,
  unit_price NUMERIC(12,2) NOT NULL,
  quantity INTEGER NOT NULL,
  seat_id UUID,
  row_label VARCHAR(40),
  seat_number INTEGER,
  ticket_payload TEXT
);
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS reservation_id UUID;
CREATE TABLE IF NOT EXISTS refund_requests (
  id UUID PRIMARY KEY,
  ticket_id UUID NOT NULL,
  order_id UUID NOT NULL REFERENCES purchase_orders(id),
  order_item_id UUID NOT NULL REFERENCES purchase_order_items(id),
  customer_id UUID NOT NULL,
  event_id UUID NOT NULL,
  amount NUMERIC(12,2) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  decision VARCHAR(16),
  progress VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
  source VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT refund_amount_positive CHECK (amount > 0)
);
ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE refund_requests DROP CONSTRAINT IF EXISTS refund_requests_ticket_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS ux_refunds_ticket_source ON refund_requests(ticket_id,source);
CREATE INDEX IF NOT EXISTS ix_refunds_customer ON refund_requests(customer_id,created_at);
CREATE TABLE IF NOT EXISTS event_cancellations (
  event_id UUID PRIMARY KEY,
  last_ticket_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
