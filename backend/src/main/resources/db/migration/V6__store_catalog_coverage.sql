-- Store coverage fixes (2026-10-09).

-- 1. Delivery terms with a small-order tier. SAS publishes (https://www.sas.am/en/delivery/, checked 2026-10-09):
--    order up to 4999 AMD -> 1500 AMD; 5000 AMD or more -> 700 AMD up to 3 km, 900 AMD from 3.1 km (Yerevan), no minimum.
--    The distance is unknown to the app, so the 3 km fee (700) is stored as the regular fee and the source says so.
--    Other stores publish no machine-readable terms: they stay NULL (= unknown, never assumed).
ALTER TABLE stores ADD COLUMN small_order_below NUMERIC(12, 2);
ALTER TABLE stores ADD COLUMN small_order_fee NUMERIC(12, 2);

UPDATE stores
SET delivery_fee              = 700,
    free_delivery_from        = NULL,
    min_order_amount          = NULL,
    small_order_below         = 5000,
    small_order_fee           = 1500,
    delivery_terms_source     = 'https://www.sas.am/en/delivery/ : <5000 AMD -> 1500; >=5000 AMD -> 700 (up to 3 km) / 900 (from 3.1 km) in Yerevan',
    delivery_terms_checked_at = TIMESTAMPTZ '2026-10-09 00:00:00+04'
WHERE slug = 'sas';

-- 2. Kitchenware named after food ("Bread boxes and baskets", "Հացաման", kettles, coffee cups) was classified into food
--    categories and could be offered as bread/tea/... in baskets. Same rule as CategoryClassifier.isNonFood; only
--    products currently in a food category are moved (personal care etc. keep their category).
UPDATE canonical_products cp
SET category_id = (SELECT id FROM categories WHERE slug = 'household'),
    updated_at  = now()
WHERE EXISTS (SELECT 1 FROM categories WHERE slug = 'household')
  AND cp.category_id IN (SELECT id FROM categories WHERE slug IN ('dairy', 'eggs', 'meat', 'fish', 'bread', 'produce', 'grains',
                                                                 'pantry', 'sweets', 'drinks', 'coffee-tea', 'alcohol', 'frozen'))
  AND EXISTS (SELECT 1
              FROM store_products sp
              WHERE sp.canonical_product_id = cp.id
                AND (lower(coalesce(sp.external_category, '')) ~ '(tableware|kitchen (tools|accessories|appliances|utensils)|sas home|սպասք|խոհանոցային|посуда|кухонн)'
                  OR lower(coalesce(sp.external_name_en, '')) ~ 'bread ?(box|bin|pin|basket)'
                  OR lower(sp.external_name) ~ '(հացաման|хлебниц)'));

-- 3. Store-scoped listing queries (GET /api/stores/{id}/products, per-store counts).
CREATE INDEX IF NOT EXISTS idx_store_products_store_active ON store_products (store_id, active);
