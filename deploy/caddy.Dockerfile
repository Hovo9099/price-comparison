# Admin web app built into the Caddy image (same origin as the API, so no CORS and VITE_API_URL stays empty).
FROM node:20-alpine AS admin
WORKDIR /admin
COPY admin/package.json admin/package-lock.json ./
RUN npm ci
COPY admin/ ./
RUN npm run build

FROM caddy:2.8-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=admin /admin/dist /srv/admin
