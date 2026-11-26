# Data sources

Every price in the system comes from a publicly accessible page or API of the store, observed by a crawler
(or from a CSV of real observations). Nothing is invented: fields a store does not publish are stored as `NULL`.

Research date: **2026-10-08**. Store markup and APIs change; parsers are covered by fixture tests
(`backend/src/test/resources/fixtures`, fictional products reproducing the real markup structure), and each
crawl logs per-item errors to `crawl_errors` so breakage is visible in the admin dashboard.

## Crawling rules applied to every source

- Only public pages/APIs that the store's own website uses without login. No CAPTCHA, login, or access-control
  bypass; no private/undocumented keys (e.g. an internal image-search API key found in the Yerevan City
  JavaScript bundle is **not** used).
- `robots.txt` is fetched and enforced for every host (`RobotsRules`, `User-agent: *` group, wildcards, `$`).
- Honest User-Agent: `WhereIsItCheapestBot/0.1 (price comparison research; +https://github.com/where-is-it-cheapest)`.
- Rate limiting per host (default ≥700 ms between requests, 350 ms for the Yerevan City JSON API), sequential
  requests per store, gzip, retries with exponential backoff on timeouts / HTTP 429 / 5xx, `Retry-After` honoured.
- One store failing never stops the others (each store runs independently and records its own crawl run).

## Summary

| Store | Status | Method | Catalog size (2026-10-08) | Barcode | Brand | SKU |
|---|---|---|---|---|---|---|
| SAS Supermarket (sas.am) | **Integrated** | HTML: menu tree → leaf sections → `?LIMIT=60&offset=N` → product pages | see import report | no | yes (product page) | yes ("Ապրանքի կոդը") |
| Yerevan City (yerevan-city.am) | **Integrated** | Public JSON API used by the storefront | see import report | **yes** | yes | no (internal id) |
| Carrefour Armenia (carrefour.am) | **Integrated** | Sitemap → product pages (schema.org microdata) | 10,475 URLs in sitemap | no | yes | yes ("ID") |
| Nor Zovq (norzovq.am) | Unavailable | — site returns Cloudflare 525 | — | — | — | — |
| Supermarket.am | **Integrated** | HTML: menu → level-2 categories → `?TOVGRUPCODE=…&PAGE_ELEMENT_COUNT=100&PAGEN_1=N` | 99 crawl units | no | no | no (internal id) |

---

## SAS Supermarket — https://www.sas.am

1C-Bitrix storefront. Armenian (`/`), Russian (`/ru/`) and English (`/en/`) versions with identical product ids.

**robots.txt** (relevant lines): disallows `/bitrix/`, `/ajax/`, `*/search/`, `/*PAGEN`, `/*sort=`, `/*order=`,
`/*?action=`, `*bitrix_*=` … and publishes `Sitemap: https://www.sas.am/sitemap.xml`.
Because `PAGEN_*` pagination is disallowed, the crawler uses the storefront's other pagination parameters
`LIMIT` and `offset`, which robots.txt allows and which the site itself renders in its pagination links.

| What | Where |
|---|---|
| Category tree | `https://www.sas.am/catalog/` — main menu `li.main-menu__item-level-{1..4} > a.main-menu__link-level-N` (436 categories, 343 leaf sections on 2026-10-08). `main-menu__link-level--empty` = no children. |
| Listing / pagination | `https://www.sas.am/catalog/{section}/?LIMIT=60&offset={0,60,120…}`. Page sizes offered by the UI: 24/36/48/60. Max offset from `.pagination__links a[href*=offset]`. **Past the last page the site returns page 1 again** → the crawler stops when the first product id repeats. Parent sections render no product grid; only leaf sections are crawled. |
| Product card (listing) | `div.product.js-product`: id `input[name=id]`, URL `a.product__cover-link`, name `.product__name`, price `.price__new .price__text`, old price `.price__old .price__text`, sale step `.price__new > span.price__currency` (e.g. `/0.5 կգ` → sold by weight), unit price `.card__kg-price` (`դր./1կգ`, `դր./1լ`), stock `[data-max-quantity]`, image `v-picture[:sources]` JSON. Out of stock: `.card__available` "Առկա չէ" and no price. |
| English names | Same listing under `/en/catalog/{section}/?LIMIT=60&offset=N` (same ids). |
| Product page | `https://www.sas.am/catalog/{section}/{id}/`: SKU `.card__code-value` ("Ապրանքի կոդը"), id `.card__id-value`, brand `.card__brand-link` ("Kühne ապրանքանիշ"), price/old price, unit price, stock `.card__availability-text` ("պահեստում: 60 հատ"), image `og:image`, breadcrumbs. |
| Product ids | Numeric Bitrix element id (e.g. `2130`), stable across languages. |
| Sitemap | `sitemap-custom-catalog-products.partN.xml` (~110k URLs incl. ru/en and inactive items) and `sitemap-custom-catalog-sections.xml` (627 URLs). Not used for prices (product pages are ~550 KB each); the menu + listings give the active catalog efficiently. |
| Not available | Barcode (EAN) is not published. |

**Virtual sections**: `new_products`, `discount` are skipped; thematic groupings (`sas_product`, `mm_gluten_free`,
`mm_lacto_free`, `mm_organic`, `mm_sugar_free`, `sas_home`, `sas_sweet`) repeat products from regular sections and
are crawled last so products keep their real category. Duplicates across sections are ignored by product id.

**Weighted goods** (meat, cheese by weight): the listing shows the price of one sale step (e.g. 3 725 ֏ per 0.5 kg)
and the price per kg (7 450 ֏/կգ). The system stores the **price per kg** with `sold_by_weight = true` so weighted
goods compare across stores; the raw step price is kept in `raw_product_snapshots`.

**Crawl phases**: (1) all leaf listings in Armenian + English → every price is captured quickly (~700 listing
requests); (2) product pages for products never enriched before (`crawler.providers.sas.detail-pages=MISSING`) →
SKU, brand, stock.

## Yerevan City — https://yerevan-city.am

Angular SPA. robots.txt: `User-agent: * / Allow: /` with a sitemap. The API host `apishopv2.yerevan-city.am`
serves a Cloudflare "content signals" robots.txt without Disallow rules. Product pages:
`https://yerevan-city.am/shop/product-details/{id}` (listed in `sitemap-products-N.xml`).

The storefront calls a JSON API (`environment.baseUrl = https://apishopv2.yerevan-city.am/`, paths
`api/{Controller}/{Action}`). No authentication is needed for catalog endpoints. Language is selected with the
`content-language` header: `1` = English, `2` = Russian, `3` = Armenian.

| What | Request |
|---|---|
| Top categories | `POST /api/Category/GetParentCategories` body `{}` → `data.categories[] {id, name, isHidden, isAdult}` (37 on 2026-10-08) |
| Category tree | `POST /api/Category/GetCategoryWeb` body `{"categoryId":22,"parentId":22,"count":1,"page":1}` → `data.categoryWithChildren` (nested `catgoryId`, `categoryName`, `children`) |
| Products (paged) | `POST /api/Product/GetByCategory` body `{"categoryId":22,"parentId":22,"count":100,"page":1}` → `data.list[] {id, name (Armenian), photo, price, discountedPrice, discountPercent, isKilogram, categoryName}`, `pageCount`, `itemCount`. A top-level id includes all sub-categories. Names in this endpoint are always Armenian. |
| Product details | `GET /api/Product/GetPublic/{id}` → `{name, category, brandName, barcode, price, discountedPrice, currencyCode, isOnline}`; honours `content-language` → used with `1` for the English name and Latin brand. |

- Current price = `discountedPrice` when > 0 (rounded to whole dram), otherwise `price`; original price = `price`
  when discounted.
- `isKilogram = true` → price is per kg (`sold_by_weight`).
- Skipped: category 1563 (tobacco). Category 40 ("Dietary, gluten free and bio") repeats other categories and is
  crawled last.
- **Barcodes (EAN-13)** are published for most products → highest-confidence matching key.

## Carrefour Armenia — https://carrefour.am

Magento 2. robots.txt disallows `/*?` (any query string), `/catalog/`, `/checkout/`, `/customer/` … and lists
`sitemap_hy.xml`, `sitemap_ru.xml`, `sitemap_en.xml`, `sitemap_fr.xml`.

Because query strings are disallowed, category pagination (`?p=2`) cannot be used. Products are discovered from
`https://carrefour.am/sitemap_hy.xml` (single-segment URLs below `/am/`, 10,475 on 2026-10-08); some sitemap
entries are delisted products that return 404 (skipped, not counted as errors).

| Field | Selector on the product page |
|---|---|
| Name | `h1.page-title [itemprop=name]` |
| SKU / external id | `[itemprop=sku]` (shown as "ID") |
| Price | `[data-price-type=finalPrice][data-price-amount]` / `meta[itemprop=price]` |
| Original price | `[data-price-type=oldPrice][data-price-amount]` |
| Brand | `.product-attribute-manufacturer` |
| Per-kg price (weighted goods) | `.attribute-weight` ("585 ֏/կգ"); the displayed price is for one weight step |
| Image | `meta[property=og:image]` |
| Availability | no price / `.stock.unavailable` → unavailable |

English names are available at the same slug under `/en/` (disabled by default: doubles requests;
`crawler.providers.carrefour.fetch-english-names=true` enables it). Barcodes are not published.

## Nor Zovq — https://norzovq.am

Every request (with or without `www`) returned **HTTP 525** (Cloudflare: SSL handshake with origin failed) on
2026-10-08. There is no public catalog to read. `NorZovqProvider.isAvailable()` probes the site each time a crawl
is requested and reports the reason; once the site is back, a parser can be added. Until then prices can only be
added through the CSV fallback.

## Supermarket.am — https://supermarket.am

1C-Bitrix storefront (Armenian). No robots.txt (the URL returns the HTML 404 page) and no sitemap.xml, so there
are no crawl restrictions beyond our own rate limit. Product names follow the same style as SAS
(`Մյուսլի «Աքսա» … 330գ`), which makes Armenian-name matching between the two effective.

| What | Where |
|---|---|
| Category tree | Home page main menu: top level `li > a[href^=/categories/] > p` with `div.sub`; inside, `li.title` = level 2, following plain `li` = level 3 children (251 categories, 99 level-2 crawl units on 2026-10-08). |
| Listing / pagination | `https://supermarket.am/categories/{slug}_{id}/?TOVGRUPCODE={id}&PAGE_ELEMENT_COUNT=100&PAGEN_1=N`. Level-2 (and top-level) pages aggregate their sub-categories. `PAGE_ELEMENT_COUNT` 20/40/60/80/100 is offered by the UI. Total from `.pages_block .pages` ("1-20 / 1157"). |
| Product card | `div.td-overally`: link/name `.h3 a` (`/products/{slug}_{id}/` or `/products/_{id}/`), hidden inputs `input.price` (current, "1,450 դր"), `input.old_price` (strikethrough, only when higher), `input.isnotquntity=1` (sold by weight, price per kg), `input.max_value_allowed`, `input.for_adult`; image `.image img`. |
| Not available | barcode, brand, SKU, English names. |

Skipped top-level sections: `novinki_8102` (new arrivals), `save_5543` (daily discounts) — both repeat regular
products — and tobacco. Diet groupings (`kholesterin_6454`, `diabeticheskie-produkty_1057`) are crawled last.

## Other candidates (not yet researched in depth)

- Evrika (evrika.am) — returned HTTP 436 to plain requests.
- Gloria (gloria.am) — redirects; online catalog not confirmed.
- City (city.am) — HTTP 401 for anonymous requests.

## CSV fallback

`POST /api/admin/import/csv` (admin UI → CSV import) accepts real observations:

```
store,externalProductId,name,brand,barcode,price,currency,available,capturedAt
```

Optional columns: `nameEn, originalPrice, url, category, imageUrl`. `store` is a store slug (`nor-zovq`, …) or
name. Rows go through the same normalization, price-history and matching pipeline; each store in a file becomes
a `CSV` crawl run with its own error log.
