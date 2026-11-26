-- Where Is It Cheapest? - core schema
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE stores (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    slug         VARCHAR(100) NOT NULL UNIQUE,
    website_url  VARCHAR(500),
    logo_url     VARCHAR(500),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE store_locations (
    id         BIGSERIAL PRIMARY KEY,
    store_id   BIGINT       NOT NULL REFERENCES stores (id),
    name       VARCHAR(300) NOT NULL,
    address    VARCHAR(500),
    latitude   NUMERIC(9, 6),
    longitude  NUMERIC(9, 6),
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_store_locations_store ON store_locations (store_id);

CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    name_en    VARCHAR(200),
    slug       VARCHAR(100) NOT NULL UNIQUE,
    icon       VARCHAR(50),
    sort_order INT          NOT NULL DEFAULT 0,
    parent_id  BIGINT REFERENCES categories (id),
    active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE canonical_products (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(500)  NOT NULL,
    name_en         VARCHAR(500),
    normalized_name VARCHAR(500)  NOT NULL,
    brand           VARCHAR(200),
    barcode         VARCHAR(50),
    size_value      NUMERIC(12, 3),
    size_unit       VARCHAR(10),
    variant         VARCHAR(100),
    sold_by_weight  BOOLEAN       NOT NULL DEFAULT FALSE,
    category_id     BIGINT REFERENCES categories (id),
    image_url       VARCHAR(1000),
    search_text     TEXT          NOT NULL DEFAULT '',
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_canonical_barcode ON canonical_products (barcode);
CREATE INDEX idx_canonical_size ON canonical_products (size_unit, size_value);
CREATE INDEX idx_canonical_category ON canonical_products (category_id);
CREATE INDEX idx_canonical_search_trgm ON canonical_products USING gin (search_text gin_trgm_ops);

CREATE TABLE store_products (
    id                   BIGSERIAL PRIMARY KEY,
    store_id             BIGINT        NOT NULL REFERENCES stores (id),
    canonical_product_id BIGINT REFERENCES canonical_products (id),
    external_product_id  VARCHAR(100)  NOT NULL,
    external_sku         VARCHAR(100),
    external_name        VARCHAR(500)  NOT NULL,
    external_name_en     VARCHAR(500),
    external_url         VARCHAR(1000),
    external_category    VARCHAR(500),
    barcode              VARCHAR(50),
    brand                VARCHAR(200),
    package_size         NUMERIC(12, 3),
    package_unit         VARCHAR(10),
    sold_by_weight       BOOLEAN       NOT NULL DEFAULT FALSE,
    image_url            VARCHAR(1000),
    details_fetched_at   TIMESTAMPTZ,
    last_seen_at         TIMESTAMPTZ,
    active               BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_store_product_external UNIQUE (store_id, external_product_id)
);
CREATE INDEX idx_store_products_canonical ON store_products (canonical_product_id);
CREATE INDEX idx_store_products_barcode ON store_products (barcode);

-- Price periods: one row per distinct price; valid_to IS NULL marks the current price.
-- captured_at is the most recent time the price was observed (used for freshness).
CREATE TABLE prices (
    id               BIGSERIAL PRIMARY KEY,
    store_product_id BIGINT         NOT NULL REFERENCES store_products (id),
    price            NUMERIC(12, 2) NOT NULL,
    original_price   NUMERIC(12, 2),
    unit_price       NUMERIC(12, 2),
    unit_price_unit  VARCHAR(10),
    currency         VARCHAR(3)     NOT NULL DEFAULT 'AMD',
    available        BOOLEAN        NOT NULL DEFAULT TRUE,
    captured_at      TIMESTAMPTZ    NOT NULL,
    valid_from       TIMESTAMPTZ    NOT NULL,
    valid_to         TIMESTAMPTZ
);
CREATE INDEX idx_prices_store_product ON prices (store_product_id, valid_from DESC);
CREATE UNIQUE INDEX uq_prices_current ON prices (store_product_id) WHERE valid_to IS NULL;

CREATE TABLE crawl_runs (
    id                  BIGSERIAL PRIMARY KEY,
    store_id            BIGINT      NOT NULL REFERENCES stores (id),
    run_type            VARCHAR(20) NOT NULL DEFAULT 'CRAWL',
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at         TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL,
    max_products        INT,
    pages_visited       INT         NOT NULL DEFAULT 0,
    products_discovered INT         NOT NULL DEFAULT 0,
    products_updated    INT         NOT NULL DEFAULT 0,
    prices_updated      INT         NOT NULL DEFAULT 0,
    errors              INT         NOT NULL DEFAULT 0,
    error_message       TEXT
);
CREATE INDEX idx_crawl_runs_store ON crawl_runs (store_id, started_at DESC);

CREATE TABLE crawl_errors (
    id           BIGSERIAL PRIMARY KEY,
    crawl_run_id BIGINT      NOT NULL REFERENCES crawl_runs (id),
    url          VARCHAR(1000),
    external_id  VARCHAR(100),
    message      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_crawl_errors_run ON crawl_errors (crawl_run_id);

-- Every observation of a price (one row per product per crawl), never updated.
CREATE TABLE price_history (
    id               BIGSERIAL PRIMARY KEY,
    store_product_id BIGINT         NOT NULL REFERENCES store_products (id),
    price            NUMERIC(12, 2) NOT NULL,
    original_price   NUMERIC(12, 2),
    available        BOOLEAN        NOT NULL,
    captured_at      TIMESTAMPTZ    NOT NULL,
    source_run_id    BIGINT REFERENCES crawl_runs (id)
);
CREATE INDEX idx_price_history_product ON price_history (store_product_id, captured_at);

CREATE TABLE product_aliases (
    id                   BIGSERIAL PRIMARY KEY,
    canonical_product_id BIGINT       NOT NULL REFERENCES canonical_products (id),
    alias                VARCHAR(500) NOT NULL,
    normalized_alias     VARCHAR(500) NOT NULL,
    source               VARCHAR(100),
    CONSTRAINT uq_alias UNIQUE (canonical_product_id, normalized_alias)
);
CREATE INDEX idx_alias_trgm ON product_aliases USING gin (normalized_alias gin_trgm_ops);

CREATE TABLE raw_product_snapshots (
    id                  BIGSERIAL PRIMARY KEY,
    store_id            BIGINT       NOT NULL REFERENCES stores (id),
    crawl_run_id        BIGINT REFERENCES crawl_runs (id),
    external_product_id VARCHAR(100) NOT NULL,
    source_url          VARCHAR(1000),
    raw_name            VARCHAR(500),
    raw_price           VARCHAR(100),
    raw_original_price  VARCHAR(100),
    raw_data            JSONB,
    captured_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_raw_snapshots_store_ext ON raw_product_snapshots (store_id, external_product_id);

CREATE TABLE product_match_candidates (
    id                   BIGSERIAL PRIMARY KEY,
    store_product_id     BIGINT        NOT NULL REFERENCES store_products (id),
    canonical_product_id BIGINT        NOT NULL REFERENCES canonical_products (id),
    confidence           NUMERIC(5, 4) NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    reason               TEXT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    reviewed_at          TIMESTAMPTZ,
    CONSTRAINT uq_match_candidate UNIQUE (store_product_id, canonical_product_id)
);
CREATE INDEX idx_match_candidates_status ON product_match_candidates (status);

CREATE TABLE shopping_lists (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE shopping_list_items (
    id                   BIGSERIAL PRIMARY KEY,
    shopping_list_id     BIGINT         NOT NULL REFERENCES shopping_lists (id) ON DELETE CASCADE,
    canonical_product_id BIGINT REFERENCES canonical_products (id),
    free_text            VARCHAR(300),
    quantity             NUMERIC(10, 3) NOT NULL DEFAULT 1,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_item_target CHECK (canonical_product_id IS NOT NULL OR free_text IS NOT NULL)
);
CREATE INDEX idx_shopping_list_items_list ON shopping_list_items (shopping_list_id);
