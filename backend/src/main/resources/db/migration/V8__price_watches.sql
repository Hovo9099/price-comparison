-- Price watches: an anonymous app install (device_id = random UUID generated on the phone) follows products and
-- optionally gets a push notification when the cheapest current price drops or reaches a target.

CREATE TABLE watch_devices (
    device_id  VARCHAR(64) PRIMARY KEY,
    push_token VARCHAR(255),
    language   VARCHAR(5)  NOT NULL DEFAULT 'hy',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE price_watches (
    id                   BIGSERIAL PRIMARY KEY,
    device_id            VARCHAR(64)   NOT NULL REFERENCES watch_devices (device_id) ON DELETE CASCADE,
    canonical_product_id BIGINT        NOT NULL REFERENCES canonical_products (id),
    target_price         NUMERIC(12, 2),
    -- cheapest price when the watch was created (shown as "since you started watching")
    start_price          NUMERIC(12, 2),
    -- price a further drop is measured against; moves down on each notification and up when the price rises
    reference_price      NUMERIC(12, 2),
    last_notified_price  NUMERIC(12, 2),
    last_notified_at     TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_price_watch UNIQUE (device_id, canonical_product_id),
    CONSTRAINT chk_target_positive CHECK (target_price IS NULL OR target_price > 0)
);
CREATE INDEX idx_price_watches_product ON price_watches (canonical_product_id);
