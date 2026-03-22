#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$ROOT_DIR/backups"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
FILE="$BACKUP_DIR/cartridge_db-$TIMESTAMP.sql"

if [[ -f "$ROOT_DIR/.env" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
fi

POSTGRES_DB="${POSTGRES_DB:-cartridge_db}"
POSTGRES_USER="${POSTGRES_USER:-cartridge_user}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"

echo "Creating backup: $FILE"
docker compose -f "$ROOT_DIR/docker-compose.yml" exec -T postgres \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" > "$FILE"

echo "Backup created: $FILE"

echo "Applying retention policy: keep last $BACKUP_RETENTION_DAYS days..."
find "$BACKUP_DIR" -type f -name "cartridge_db-*.sql" -mtime +"$BACKUP_RETENTION_DAYS" -print -delete || true
echo "Retention check complete."
