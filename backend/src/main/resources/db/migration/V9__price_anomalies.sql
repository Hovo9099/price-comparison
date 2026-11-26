-- Suspicious prices (e.g. a crawler reading the price per 100 g, a missing digit, a placeholder price) are stored as
-- observed but flagged, never chosen as cheapest, and queued for review.
ALTER TABLE prices ADD COLUMN suspect BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE price_anomalies (
    id               BIGSERIAL PRIMARY KEY,
    price_id         BIGINT         NOT NULL REFERENCES prices (id),
    store_product_id BIGINT         NOT NULL REFERENCES store_products (id),
    -- SUDDEN_DROP / SUDDEN_RISE (vs the listing's last trusted price), BELOW_MARKET / ABOVE_MARKET (vs other stores)
    kind             VARCHAR(20)    NOT NULL,
    new_price        NUMERIC(12, 2) NOT NULL,
    reference_price  NUMERIC(12, 2) NOT NULL,
    -- PENDING; CONFIRMED = the price is real; REJECTED = the price is wrong; SUPERSEDED = a newer price replaced it
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    detected_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    reviewed_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_price_anomalies_price ON price_anomalies (price_id);
CREATE INDEX idx_price_anomalies_status ON price_anomalies (status, detected_at DESC);
