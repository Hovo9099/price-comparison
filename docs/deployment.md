# Production deployment

One Linux server with Docker Compose v2.24+ and a domain whose DNS `A`/`AAAA` record points to it.

```
Internet ──443──► caddy (HTTPS, Let's Encrypt) ──► backend:8080 (/api, /actuator/health, OpenAPI)
                        └─► /srv/admin (admin web app, same origin)
               backend ──► postgres (not published)        backup ──► ./backups/*.dump (daily)
```

## 1. Configure

```bash
cp .env.example .env
# required: POSTGRES_PASSWORD, ADMIN_TOKEN (openssl rand -hex 24), DOMAIN, ACME_EMAIL
# recommended: CRAWLER_SCHEDULE_CRON (default in prod: daily 05:00 Yerevan), ALERT_* for crawl alerts
```

## 2. Start

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f caddy backend
```

Caddy obtains the certificate on first start (ports 80 and 443 must be reachable). Open `https://<DOMAIN>/` for the
admin app (Settings → paste `ADMIN_TOKEN`) and check `https://<DOMAIN>/actuator/health`.

The overlay removes the host ports of PostgreSQL and the backend, sets `SERVER_FORWARD_HEADERS_STRATEGY=native` (the
backend sees client IPs; forwarded headers are trusted only from private-network proxies) and restricts CORS to the
domain.

## 3. Protection and caching

- **Rate limit**: `RATE_LIMIT_PER_MINUTE` (default 240) public API requests per client IP and minute, bursts up to
  the same number; over the limit → `429` with `Retry-After`. Admin endpoints are not limited (they need the token).
- **HTTP caching**: product, deal and store reads are `Cache-Control: public, max-age=60`, categories and lexicon 10
  minutes; shopping lists, watches, devices and admin are `no-store`.
- **Headers**: HSTS, `nosniff`, `no-referrer`, `DENY` framing, compression (zstd/gzip).

## 4. Backups

The `backup` service runs `deploy/backup.sh`: a `pg_dump --format=custom` every day at `BACKUP_HOUR` (UTC, default
1 = 05:00 Yerevan) into `BACKUP_DIR` (default `./backups`), keeping `BACKUP_KEEP_DAYS` (default 14) days. Copy that
directory off the server (e.g. `rclone`, provider snapshots) — a backup on the same disk does not survive the disk.

```bash
# backup now
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec backup /backup.sh once
# restore (stops the backend first; replaces the database content)
docker compose -f docker-compose.yml -f docker-compose.prod.yml stop backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T backup \
  pg_restore --clean --if-exists --no-owner -d "$POSTGRES_DB" < backups/pricecompare-YYYYMMDDTHHMMSSZ.dump
docker compose -f docker-compose.yml -f docker-compose.prod.yml start backend
```

## 5. Mobile app builds

```bash
cd mobile
npx eas init                       # once: creates the EAS project (also needed for push notifications)
EXPO_PUBLIC_API_URL=https://<DOMAIN> npx eas build --profile production --platform all
```

`eas.json` sets `EXPO_PUBLIC_API_URL` per profile; edit the production URL there before building.

## 6. Updating

```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Flyway migrates the database on backend start; take a manual backup first for releases with new migrations.
