-- Branches entered by an admin (from the store's published address list) next to crawled ones.
-- Crawler location sync never modifies or deactivates manual rows.
ALTER TABLE store_locations ADD COLUMN manual BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE store_locations ADD COLUMN source VARCHAR(500);
CREATE INDEX idx_store_locations_active ON store_locations (active) WHERE latitude IS NOT NULL;
