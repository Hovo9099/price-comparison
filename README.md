# Where Is It Cheapest? · Որտե՞ղ է ավելի էժան

Mobile-first grocery price comparison for Yerevan, built on **real prices crawled from real Armenian online
supermarkets**. Search a product in Armenian or English, compare current prices across stores (with the time each
price was captured), keep a shopping list, and compute the cheapest single-store and multi-store basket.

```
price-comparison/
├── backend/   Java 21 · Spring Boot 3.3 · JPA/Hibernate · PostgreSQL · Flyway · OpenAPI — crawlers + REST API
├── mobile/    React Native · Expo SDK 52 · TypeScript · React Navigation · TanStack Query (Armenian-first UI)
├── admin/     React · Vite · TypeScript — crawler dashboard, product mapping, match review, CSV import
├── docs/      data-sources.md (every store researched), architecture.md
└── docker-compose.yml   PostgreSQL + backend
```

## Data sources

| Store | Status | How |
|---|---|---|
| SAS Supermarket | integrated | public HTML catalog (menu → leaf sections → `?LIMIT=60&offset=N` → product pages) |
| Yerevan City | integrated | public JSON API used by its storefront (includes **barcodes**) |
| Carrefour Armenia | integrated | sitemap → product pages (schema.org microdata) |
| Nor Zovq | unavailable | site returns Cloudflare 525; CSV import only |
| Supermarket.am | integrated | public HTML catalog (menu → level-2 categories → 100-item pages) |

Details, selectors, endpoints and robots.txt rules: [docs/data-sources.md](docs/data-sources.md).
Crawlers respect robots.txt, use an identifying User-Agent, rate-limit per host and never bypass logins/CAPTCHAs.

## Prerequisites

- Java 21, Maven 3.8+
- Node.js 18.18+ (Node 20 LTS recommended) and npm
- Docker + Docker Compose (for PostgreSQL), **or** any PostgreSQL 14+ you run yourself
- For the mobile app: the Expo Go app on a phone, or an Android emulator / iOS simulator

## 1. Configure

```bash
cd price-comparison
cp .env.example .env
# edit .env: set POSTGRES_PASSWORD and ADMIN_TOKEN to long random strings, e.g.
#   openssl rand -hex 24
```

## 2. Start PostgreSQL

```bash
docker compose up -d postgres
```

No Docker and no root? `scripts/local-postgres.sh start` downloads official PostgreSQL 16 binaries into
`~/.local/share/wic-pg`, initializes a cluster on port 5433 and creates the `pricecompare` database
(`scripts/local-postgres.sh env` prints the matching `DB_*` exports). Or use any PostgreSQL ≥ 14 and create a
database + user, then point the backend at it with
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (see step 4). The `pg_trgm` extension must be available (it ships with
standard PostgreSQL packages; the first migration runs `CREATE EXTENSION IF NOT EXISTS pg_trgm`).

## 3. Run migrations

Flyway runs automatically when the backend starts. To run migrations only (and exit):

```bash
cd backend
set -a; source ../.env; set +a
mvn -q -DskipTests package
export DB_URL=jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/$POSTGRES_DB DB_USERNAME=$POSTGRES_USER DB_PASSWORD=$POSTGRES_PASSWORD
java -jar target/price-comparison-backend.jar --spring.main.web-application-type=none --app.migrate-only=true
```

Migrations live in `backend/src/main/resources/db/migration` (`V1` schema, `V2` real stores + categories —
no products or prices, `V3` crawl heartbeat, `V4` activates Supermarket.am, `V5` smart shopping list, `V6` catalog
coverage, `V7` Parma, `V8` price watches, `V9` price anomalies).

## 4. Start the backend

Either in Docker:

```bash
docker compose up -d --build backend        # http://localhost:8080
```

or locally:

```bash
cd backend
set -a; source ../.env; set +a
export DB_URL=jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/$POSTGRES_DB DB_USERNAME=$POSTGRES_USER DB_PASSWORD=$POSTGRES_PASSWORD
mvn spring-boot:run                          # http://localhost:8080
```

- Swagger UI: http://localhost:8080/swagger-ui.html · OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health
- Use `SERVER_PORT=8081` if port 8080 is taken.

## 5. Import real data

The database starts empty: prices only come from crawlers (or CSV of real observations).

**Run one supermarket crawler** (development limit of 100 products; same `DB_*` variables as above):

```bash
cd backend
java -jar target/price-comparison-backend.jar --spring.main.web-application-type=none \
     --crawler.run=sas --crawler.max-products=100
# other store codes: yerevan-city, carrefour, supermarket-am
```

**Full catalog import** (`max-products=0` = unlimited; all available stores in parallel, each host rate-limited):

```bash
java -jar target/price-comparison-backend.jar --spring.main.web-application-type=none \
     --crawler.run=all --crawler.parallel=true --crawler.max-products=0
```

Expected duration on first import: SAS ≈ 15 min for all listings (prices) + several hours of product pages
(SKU/brand, only for products never enriched); Yerevan City ≈ 2–3 h (one detail call per product for barcode and
English name); Carrefour ≈ 3 h (10k product pages). Later runs only refresh listings and enrich new products.

The same can be done from the admin UI (Crawlers → Run / Full import) or the API:

```bash
curl -X POST -H "X-Admin-Token: $ADMIN_TOKEN" "http://localhost:8080/api/admin/crawlers/sas/run?maxProducts=100"
```

Scheduled crawls: set `CRAWLER_SCHEDULE_CRON="0 0 5 * * *"` (daily 05:00 Yerevan time).

**Re-run product matching** over all stored products after changing matching rules (no re-crawl):

```bash
java -jar target/price-comparison-backend.jar --spring.main.web-application-type=none --matching.rebuild=true
```

## 6. Start the admin UI

```bash
cd admin
npm install
BACKEND_URL=http://localhost:8080 npm run dev   # http://localhost:5173 (proxies /api to the backend)
```

Open Settings and paste the `ADMIN_TOKEN`. (When the backend runs without `ADMIN_TOKEN`, admin endpoints accept
requests from localhost only.) Production build: `npm run build` (set `VITE_API_URL` to the backend URL).

## 7. Start the mobile app

```bash
cd mobile
npm install
EXPO_PUBLIC_API_URL=http://<your-computer-LAN-IP>:8080 npx expo start
```

Scan the QR code with Expo Go (Android/iOS), or press `a` (Android emulator) / `i` (iOS simulator).
Defaults without `EXPO_PUBLIC_API_URL`: `http://10.0.2.2:8080` on the Android emulator, `http://localhost:8080`
on iOS. The server address can also be changed in the app (Պրոֆիլ → Սերվերի հասցե).

Release builds should talk to the backend over HTTPS (Android blocks clear-text HTTP outside development).

Screens: Shopping list (multi-product input with suggestions, type choices, size chips, quantities, saved lists),
Results (Cheapest / Mid-range / Premium tabs), Preferences, Home (search, categories, recent searches, price drops, shopping list), Search, Product details
(cheapest price, store comparison with "updated X ago", add to list, price-history chart), Price comparison,
Shopping list, Basket optimization, Barcode scanner (Home → Սկանավորել; opens the product's store-by-store prices), Deals tab (real vs questionable discounts), Price watches (product page → Հետևել գնին; Profile → Հետևվող ապրանքներ), Profile (language հայերեն/English, server, data sources).

## 8. Run tests

```bash
cd backend && mvn test        # 80+ tests; integration tests run on an embedded real PostgreSQL (no Docker needed)
cd mobile  && npm test && npm run typecheck
cd admin   && npm run build && npm run lint
```

Test fixtures are fictional products that reproduce the real markup structure; they are test-only and never
loaded into a real database.

## Smart shopping list

Type a whole list in one field, in Armenian, Armenian written in Latin letters, Russian or English:

```
kat, dzu 10, 2 hac, karag, coca cola 1.5l, kartofil 2kg, panir
```

- **Lexicon** (`backend/src/main/resources/lexicon/grocery-concepts.json`, 45 concepts): aliases such as
  `kat / կաթ / milk / молоко`, word rules that decide which real products belong to a concept (butter never matches
  margarine, Coca-Cola never matches Coca-Cola Zero, coconut milk is not milk), default and common package sizes.
  Matching is local and deterministic (script-independent keys + edit distance for typos); no AI calls.
  Add a concept or alias by editing the JSON file and restarting the backend.
- **Parser**: splits on commas/semicolons/new lines/"and", reads quantities (`2 kat`, `x3`), sizes (`1l`, `500g`,
  `10 hat`), percentages (`3.2%`), extra words become a brand hint (`kat marianna`). Broad words (`panir`, `mis`,
  `hav`, `масло`) are recognised but need a type choice (Lori/Chanakh cheese, beef/pork, chicken breast/whole…).
- **Sizes**: a request matches products within ±10 % of the requested size; 1 L never matches 500 ml.
- **Three strategies** (`POST /api/shopping-lists/{id}/compare`), each with a single-store and a multi-store basket:
  - **Cheapest** – any comparable product, lowest price.
  - **Mid-range** – products whose price per litre/kg/piece lies between the 25th and 75th percentile of comparable
    products (mainstream band).
  - **Premium** – top quartile by price per unit (with 2–3 products the highest-priced; a single product is reported as
    "no premium alternative").
  Selection is by price position only; no quality is claimed. Every line shows the price position, number of
  comparable products, freshness and the store link.
- **Preferences**: number of stores (any/1/2/3), include delivery fees when known, use stale (3–7 days) prices,
  preferred brands. Delivery fees are stored per store (`stores.delivery_fee`, `free_delivery_from`,
  `min_order_amount`, small-order tier; edited in Admin → Delivery terms with the source page, shown in `/api/stores` as `delivery`) and are **unknown (NULL) until verified from the store's published terms**; unknown fees are
  never assumed and are disclosed. Travel costs are never estimated.
- **Matching confidence**: ≥ 0.95 merged automatically, 0.80–0.94 queued for review, below 0.80 never merged.
  Critical attributes always win over similarity: size, fat %, multipack, sugar-free/zero/light, lactose-free,
  margarine/spread vs butter.

## REST API (public)

| Method | Path | |
|---|---|---|
| GET | `/api/products/search?q=&storeId=` | search every store (hy/en/ru, units normalized); each item has one `offers[]` entry per store listing; `stores[]` gives per-store match counts and catalog status |
| GET | `/api/products?categoryId=` | browse |
| GET | `/api/products/{id}` | details |
| GET | `/api/products/by-barcode/{code}` | product by scanned EAN-13/EAN-8/UPC-A barcode (UPC-A ≡ EAN-13 with leading 0); 404 if no barcode-publishing store lists it |
| GET | `/api/products/{id}/offers` | store-by-store comparison: one row per supermarket with its own listing/price, or `NOT_FOUND` (only after a complete crawl), `NOT_YET_IMPORTED`, `CATALOG_UNAVAILABLE`, `NOT_INTEGRATED` |
| GET | `/api/products/{id}/prices` | current price per store, cheapest, `capturedAt`, `ageHours`, `freshness` |
| GET | `/api/products/{id}/history?days=90` | price observations per store |
| GET | `/api/products/price-drops` | recent price drops |
| GET | `/api/deals?categoryId=&storeId=&includeInflated=` | current discounts with a verdict: `GENUINE` (old price charged within 90 days), `INFLATED` (never charged – hidden by default), `UNVERIFIED` (< 14 days of history) |
| GET | `/api/categories` | reference data |
| GET | `/api/stores` | stores with `catalogState` (COMPLETE / PARTIAL / NOT_IMPORTED / UNAVAILABLE / NOT_IMPLEMENTED), listing counts, last complete crawl, last price update |
| GET | `/api/stores/{id}/products?q=&availableOnly=` | a store's own listings with current prices (paged) |
| POST | `/api/shopping-lists` | create list |
| GET | `/api/shopping-lists/{id}` | list with cheapest price per item |
| POST | `/api/shopping-lists/{id}/items` | add product (`productId`) or note (`freeText`) |
| PATCH/DELETE | `/api/shopping-lists/{id}/items/{itemId}` | change quantity / remove |
| POST | `/api/shopping-lists/{id}/optimize` | cheapest single-store and multi-store basket (`{"maxStores":2}` optional) |
| POST | `/api/shopping-lists/parse` | recognise free text (`{"text":"kat, dzu, panir"}`) |
| POST | `/api/shopping-lists/{id}/parse` | recognise and add (`{"text":"...","add":true}`) |
| POST | `/api/shopping-lists/{id}/compare` | cheapest / mid-range / premium baskets (`{"maxStores":2,"includeDelivery":true,"allowStalePrices":false,"preferredBrands":["Marianna"]}`) |
| GET | `/api/shopping-lists?ids=1,2` | saved lists |
| GET | `/api/lexicon/suggest?q=kar` | concept suggestions while typing |
| GET | `/api/lexicon/concepts` | all concepts with sizes and type choices |
| GET | `/api/products/{id}/price-history` | price history (alias of `/history`) |
| PUT | `/api/devices/me` | register this install's Expo push token + language (header `X-Device-Id`) |
| GET | `/api/watches` | watched products with current cheapest price (header `X-Device-Id`) |
| PUT/DELETE | `/api/watches/{productId}` | follow (optional `{"targetPrice":500}`) / stop following a product |
| GET | `/api/crawler/status` | per store: products, last successful collection, freshness, last attempt |

Admin endpoints (`/api/admin/**`, header `X-Admin-Token`): dashboard (per store: implemented, catalog state, last
crawl / last complete crawl, discovered / imported / available listings, errors, pagination, last price update),
run (`POST /api/admin/crawlers/{slugOrId}/run`) / cancel crawlers, crawl history and errors, canonical products (edit), store products (map), match candidates (approve/reject), CSV import.

**Catalog completeness**: a store's catalog is COMPLETE only after an unlimited crawl finished without being
cancelled. Only then does a missing product mean "not sold here"; otherwise the app says the catalog is not
(fully) imported or not integrated. Nor Zovq has no public catalog (Cloudflare 525) and is reported as not
integrated; the dashboard's "Check site" re-probes it.

**Price watches**: the app follows products for an anonymous install (random UUID kept on the phone, sent as
`X-Device-Id`). After every crawl that changed prices, each watch is compared with the cheapest non-outdated price:
without a target a drop of ≥ 2 % notifies; with a target price the user is notified once per new low at or below it
(re-armed when the price goes back above). Notifications go through the Expo push service (`PUSH_ENABLED`,
optional `EXPO_ACCESS_TOKEN`); tokens Expo reports as unregistered are cleared. Push tokens require an EAS project:
run `npx eas init` in `mobile/` once (adds `extra.eas.projectId` to app.json). Without it, or without permission,
watches still work and drops are shown under Profile → Watched products.

**Price anomalies**: when a listing's new price is ≤ 40 % or ≥ 2.5 × its last trusted price, or ≤ 25 % / ≥ 4 × the
median of other stores for the same product, the price is stored as observed but flagged `suspect`: it is shown, but
never used as the cheapest price, in baskets, deals, price drops or notifications. Admin → Price anomalies confirms
(price is real) or rejects it; a newer price from the store supersedes a pending one.

**Freshness**: FRESH < 24 h, RECENT 24–72 h, STALE 3–7 days, OUTDATED > 7 days. Outdated prices are shown with
their age but never chosen as "cheapest" or used in basket optimization.

See [docs/architecture.md](docs/architecture.md) for the data model, price history, matching and optimization.
