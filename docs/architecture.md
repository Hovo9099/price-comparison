# Architecture

## Overview

```
 sas.am (HTML)        yerevan-city.am (JSON API)      carrefour.am (sitemap + HTML)     CSV (real observations)
      │                         │                                 │                               │
 SasProvider          YerevanCityProvider               CarrefourProvider                 CsvImportService
      └──────────────┬──────────┴─────────────────────────────────┘                               │
                     ▼  DiscoveredProduct (exactly what the store published; unknown fields = null)│
              CrawlerService ── crawl_runs / crawl_errors (stats, heartbeat, per-item errors)       │
                     ▼                                                                             │
              ProductIngestionService  (one transaction per product) ◄─────────────────────────────┘
                ├─ store_products        upsert by (store, external_product_id); never null-out known values
                ├─ raw_product_snapshots raw payload of every observation (JSONB)
                ├─ PriceRecorder         prices (periods) + price_history (observations)
                └─ MatchingService       canonical_products, product_aliases, product_match_candidates
                     ▼
              REST API (Spring MVC, DTOs only, Bean Validation, OpenAPI)
                ├─ /api/**        mobile app (Expo)
                └─ /api/admin/**  admin app (Vite)   — X-Admin-Token
```

All store-specific code lives in `backend/src/main/java/am/pricecompare/crawler/<store>/`. Generic services never
parse store markup. Providers implement:

```java
interface SupermarketProvider {
    String getStoreCode();            // = stores.slug
    String getStoreName();
    boolean isAvailable();
    String unavailableReason();
    List<DiscoveredCategory> discoverCategories();
    void discoverProducts(CrawlContext ctx);   // streams products to ctx.emit(...)
}
```

`discoverProducts` streams instead of returning a `List` so a provider can process tens of thousands of products
with bounded memory and so `crawler.max-products` (0 = unlimited) and cancellation are honoured mid-crawl.
Each store runs on its own thread; an exception in one provider marks only that run `FAILED`.

## Data model

Tables (Flyway `V1__init_schema.sql`): `stores`, `store_locations`, `categories`, `canonical_products`,
`store_products`, `prices`, `price_history`, `product_aliases`, `crawl_runs`, `crawl_errors`,
`raw_product_snapshots`, `product_match_candidates`, `shopping_lists`, `shopping_list_items`.

Additions to the requested schema and why:

| Column / table | Reason |
|---|---|
| `store_products.external_name_en` | stores publish English names; they drive cross-store matching and English search |
| `store_products.external_category`, `sold_by_weight`, `details_fetched_at`, `last_seen_at` | category mapping, per-kg comparison, enrichment bookkeeping, disappearance detection |
| `prices.unit_price`, `unit_price_unit` | stores publish price per kg / per litre |
| `canonical_products.name_en`, `variant`, `sold_by_weight`, `search_text` | display, attribute matching (fat %, multipack), trigram search |
| `crawl_runs.run_type`, `max_products`, `heartbeat_at` | CRAWL vs CSV, dev limits, orphaned-run detection across processes |
| `crawl_errors` | per-item errors inspectable in the admin UI |
| `shopping_lists`, `shopping_list_items` | shopping list API |

## Price history

Two complementary tables; neither is ever overwritten or deleted:

- **`price_history`** – one immutable row per observation (per product per crawl run). An identical observation in
  the same run (e.g. listing + product page) is recorded once.
- **`prices`** – price periods. When price, original price or availability changes, the current row gets
  `valid_to = captured_at` and a new row is opened (`uq_prices_current` guarantees one open row per store product).
  An unchanged observation only moves `captured_at` forward — that timestamp is the "last updated" shown to users.

Example: 620 ֏ on Oct 1, 600 ֏ on Oct 3, 590 ֏ on Oct 6 → three periods (620 closed at Oct 3, 600 closed at Oct 6,
590 open) and three history rows. Current price = 590 ֏.

A product missing from a **complete** crawl (unlimited, not cancelled, error rate below 5%) is set
`active = false` and gets a new period with `available = false` (last known price kept). Out-of-stock listings
("Առկա չէ") are recorded the same way. Rows are never deleted.

## Freshness

`Freshness.of(captured_at)`: FRESH < 24 h, RECENT 24–72 h, STALE 3–7 days, OUTDATED > 7 days. Every price DTO
carries `capturedAt`, `ageHours` and `freshness`. OUTDATED prices are listed but never chosen as cheapest and are
excluded from basket optimization; the basket response flags `containsStalePrices`.

## Normalization (`ProductNormalizer`)

- NFKD + diacritics removal, lower case, punctuation → spaces, decimal comma → dot.
- Units → base units: `1.5L`, `1,5 լ`, `1500 մլ`, `1.5 л` → `1500ml`; `1 kg`, `1կգ` → `1000g`; `10 հատ`/`шт`/`pcs` → `10pcs`.
- Multipacks (`6x0.5l`, `0.5l x 4`), percentages (`3.2%` fat / alcohol), quoted product lines («…», "…").
- `Transliterator`: Armenian and Cyrillic → Latin, plus a phonetic key (c/k/q, w/v, ph/f, y/i, doubled letters),
  so `Կոկա-Կոլա`, `Кока-Кола` and `Coca-Cola` share the key `koka kola`.

## Product matching

`ProductMatcher.score(a, b)` (pure, unit tested):

1. **Barcode**: equal → 1.0 (auto); different → never the same product.
2. **Hard constraints** (any violation → 0): sold-by-weight flag, multipack count, size (±1 %, same unit),
   percentage (milk 2.5 % ≠ 3.2 %), explicit brands that differ, quoted product lines with no common token.
3. **Similarity**: weighted soft-Dice over phonetic token keys (exact 1.0, prefix 0.85, edit distance 1: 0.8),
   max over hy/en/cross-language name pairs. Weights are IDF from the canonical catalog, so "wine", "milk",
   "dry" count little and "Zorah", "Parmalat" count a lot. A matching brand adds a floor: `0.25 + 0.75 × sim`.
4. Penalties when size or percentage is known on one side only.

`MatchingService` keeps an in-memory `CanonicalIndex` (features bucketed by size/unit/sold-by-weight, barcode map,
document frequencies). A new store product is scored only against candidates in its bucket that share a token or
the brand. Decisions: **≥ 0.90** auto-link · **0.60–0.90** own canonical product + `PENDING` candidate for review ·
**< 0.60** new canonical product. A canonical product never receives two products of the same store
automatically. Approving a candidate moves the store product; an emptied canonical product is deactivated and its
shopping-list references re-pointed. Ingestion transactions are serialized so matching never sees another crawl's
uncommitted data. `--matching.rebuild=true` re-runs matching for all stored products (stores that publish
barcodes first).

## Search

`GET /api/products/search?q=` normalizes the query like product names; each token must appear in
`canonical_products.search_text` (normalized + Latin + phonetic forms of every store name; trigram GIN index) as
typed, transliterated, phonetic, a bilingual grocery synonym (կաթ/milk/молоко, ձու/հավկիթ/egg …) or, for bare
numbers, a size (`1.5` → `1500ml`/`1500g`). Ranking: whole word > word prefix > substring, product type first,
query category match (milk → dairy), query coverage, number of stores. Misspellings fall back to
`word_similarity`.

Candidates are ranked in SQL (same whole word / prefix / substring scoring) before the Java ranking sees them: the
600 best matches overall **plus the 150 best of every store**. (An earlier unordered `LIMIT 600` returned whichever
rows Postgres read first, so a query matching thousands of products showed mostly one store, depending on the
query's language.) Multi-word phrases (`cooking oil`, `sour cream`) are rewritten to a single synonym first. The
response lists every store's own offer per product and per-store match counts with the store's catalog state.

## Basket optimization

`BasketOptimizer` (pure, deterministic, unit tested): each item's offer per store = that store's cheapest usable
current price. Single store = cheapest store that has every item available anywhere. Multi-store = exhaustive
search over all store subsets (≤ 15 stores, optionally `maxStores`), each item bought at its cheapest store in the
subset; maximize coverage, then minimize total, then fewer stores. Savings = single − multi.
