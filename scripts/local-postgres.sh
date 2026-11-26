#!/usr/bin/env bash
# Runs a real PostgreSQL 16 without Docker or root, for development machines where `docker compose` is not
# available. Binaries come from Maven Central (io.zonky.test.postgres, official PostgreSQL builds).
#
#   scripts/local-postgres.sh start    # init (first time) + start on port 5433, creates db/user "pricecompare"
#   scripts/local-postgres.sh stop
#   scripts/local-postgres.sh env      # prints DB_URL / DB_USERNAME / DB_PASSWORD exports for the backend
set -euo pipefail

PG_VERSION=16.4.0
BASE="${PG_HOME:-$HOME/.local/share/wic-pg}"
BIN="$BASE/pgsql/bin"
DATA="$BASE/data"
PORT="${PG_PORT:-5433}"

install() {
  [ -x "$BIN/postgres" ] && return
  mkdir -p "$BASE/pgsql" "$BASE/dl"
  local arch=amd64; [ "$(uname -m)" = "aarch64" ] && arch=arm64v8
  local os=linux; [ "$(uname)" = "Darwin" ] && os=darwin
  local jar="embedded-postgres-binaries-$os-$arch-$PG_VERSION.jar"
  curl -fL -o "$BASE/dl/$jar" "https://repo1.maven.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-$os-$arch/$PG_VERSION/$jar"
  (cd "$BASE/dl" && unzip -o -q "$jar" && tar -xJf ./*.txz -C "$BASE/pgsql")
}

init() {
  [ -f "$DATA/PG_VERSION" ] && return
  umask 077
  openssl rand -hex 16 > "$BASE/.superuser-pw"
  openssl rand -hex 16 > "$BASE/.app-pw"
  "$BIN/initdb" -D "$DATA" -U postgres --pwfile="$BASE/.superuser-pw" -A scram-sha-256 -E UTF8 --locale=C.UTF-8 >/dev/null
  # Create the application role and database in single-user mode (no psql client needed).
  printf "CREATE ROLE pricecompare LOGIN PASSWORD '%s';\nCREATE DATABASE pricecompare OWNER pricecompare;\n" \
    "$(cat "$BASE/.app-pw")" | "$BIN/postgres" --single -D "$DATA" postgres >/dev/null
}

case "${1:-start}" in
  start)
    install; init
    "$BIN/pg_ctl" -D "$DATA" status >/dev/null 2>&1 || "$BIN/pg_ctl" -D "$DATA" -o "-p $PORT -k /tmp" -l "$BASE/pg.log" -w start
    echo "PostgreSQL running on port $PORT (data: $DATA)";;
  stop) "$BIN/pg_ctl" -D "$DATA" stop;;
  env)
    echo "export DB_URL=jdbc:postgresql://localhost:$PORT/pricecompare"
    echo "export DB_USERNAME=pricecompare"
    echo "export DB_PASSWORD=$(cat "$BASE/.app-pw" 2>/dev/null || echo '<unknown>')";;
  *) echo "usage: $0 start|stop|env" >&2; exit 2;;
esac
