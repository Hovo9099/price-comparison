-- Smart shopping list: items can request a product concept ("milk 1L") instead of one specific product,
-- stores can carry known delivery fees, and comparison results are kept for auditing.

ALTER TABLE shopping_list_items ADD COLUMN concept_code VARCHAR(60);
ALTER TABLE shopping_list_items ADD COLUMN raw_text VARCHAR(300);
ALTER TABLE shopping_list_items ADD COLUMN size_value NUMERIC(12, 3);
ALTER TABLE shopping_list_items ADD COLUMN size_unit VARCHAR(10);
ALTER TABLE shopping_list_items ADD COLUMN preferred_brand VARCHAR(100);
ALTER TABLE shopping_list_items ADD COLUMN percent NUMERIC(5, 2);
ALTER TABLE shopping_list_items DROP CONSTRAINT chk_item_target;
ALTER TABLE shopping_list_items ADD CONSTRAINT chk_item_target
    CHECK (canonical_product_id IS NOT NULL OR concept_code IS NOT NULL OR free_text IS NOT NULL);

-- Delivery terms are NULL (= unknown) unless confirmed from the store's published terms.
ALTER TABLE stores ADD COLUMN delivery_fee NUMERIC(12, 2);
ALTER TABLE stores ADD COLUMN free_delivery_from NUMERIC(12, 2);
ALTER TABLE stores ADD COLUMN min_order_amount NUMERIC(12, 2);
ALTER TABLE stores ADD COLUMN delivery_terms_source VARCHAR(500);
ALTER TABLE stores ADD COLUMN delivery_terms_checked_at TIMESTAMPTZ;

CREATE TABLE basket_comparisons (
    id               BIGSERIAL PRIMARY KEY,
    shopping_list_id BIGINT      NOT NULL REFERENCES shopping_lists (id) ON DELETE CASCADE,
    preferences      JSONB       NOT NULL,
    result           JSONB       NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_basket_comparisons_list ON basket_comparisons (shopping_list_id, created_at DESC);
