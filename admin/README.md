# Admin UI

React + Vite + TypeScript + TanStack Query. Talks to the backend's `/api/admin/**` endpoints with the
`X-Admin-Token` header (set it under **Settings**).

```bash
npm install
BACKEND_URL=http://localhost:8080 npm run dev   # http://localhost:5173, /api is proxied to BACKEND_URL
npm run build                                    # production build in dist/ (set VITE_API_URL to the backend URL)
npm run lint
```

Pages:

- **Crawlers** – per store: status, last crawl, products (active / total), prices updated, errors; run with a dev
  limit, full import, cancel. Auto-refreshes while a crawl runs (also crawls started from the CLI).
- **Crawl history** – every crawl/CSV run with statistics; click *Errors* to inspect logged errors.
- **Canonical products** – search by name/barcode, edit name/brand/barcode/size/category/active, see linked
  store products with current prices.
- **Store products & mapping** – filter by store / unmatched, map a store product to a canonical product.
- **Match review** – uncertain matches (60–90 %): Approve, Reject, or Search product to map elsewhere.
- **CSV import** – upload real price observations for stores that cannot be crawled.
