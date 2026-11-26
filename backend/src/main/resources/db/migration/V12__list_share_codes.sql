-- A shopping list can be opened on other phones (family, own second device) with a random share code.
ALTER TABLE shopping_lists ADD COLUMN share_code VARCHAR(12);
CREATE UNIQUE INDEX uq_shopping_lists_share_code ON shopping_lists (share_code) WHERE share_code IS NOT NULL;
