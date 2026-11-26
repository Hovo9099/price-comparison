#!/bin/sh
# Daily PostgreSQL backups (custom format, restorable with pg_restore) kept for BACKUP_KEEP_DAYS days.
# Runs inside the "backup" service of docker-compose.prod.yml; also usable by hand:
#   docker compose -f docker-compose.yml -f docker-compose.prod.yml exec backup /backup.sh once
set -eu

: "${PGHOST:=postgres}"
: "${BACKUP_DIR:=/backups}"
: "${BACKUP_KEEP_DAYS:=14}"
: "${BACKUP_HOUR:=3}"

dump() {
	ts=$(date -u +%Y%m%dT%H%M%SZ)
	file="$BACKUP_DIR/${PGDATABASE}-$ts.dump"
	pg_dump --format=custom --compress=6 --no-owner --file="$file.partial"
	mv "$file.partial" "$file"
	echo "backup: wrote $file ($(du -h "$file" | cut -f1))"
	find "$BACKUP_DIR" -name "${PGDATABASE}-*.dump" -mtime +"$BACKUP_KEEP_DAYS" -print -delete | sed 's/^/backup: removed /'
}

mkdir -p "$BACKUP_DIR"
if [ "${1:-loop}" = "once" ]; then
	dump
	exit 0
fi

until pg_isready -q; do sleep 5; done
echo "backup: daily at ${BACKUP_HOUR}:00 UTC, keeping ${BACKUP_KEEP_DAYS} days in $BACKUP_DIR"
while true; do
	now=$(date -u +%s)
	next=$(( now - now % 86400 + BACKUP_HOUR * 3600 ))
	[ "$next" -le "$now" ] && next=$(( next + 86400 ))
	sleep $(( next - now ))
	dump || echo "backup: FAILED" >&2
done
