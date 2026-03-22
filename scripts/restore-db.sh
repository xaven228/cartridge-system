#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: ./scripts/restore-db.sh <path-to-backup.sql>"
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_FILE="$1"

if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "Backup file not found: $BACKUP_FILE"
  exit 1
fi

if [[ -f "$ROOT_DIR/.env" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
fi

POSTGRES_DB="${POSTGRES_DB:-cartridge_db}"
POSTGRES_USER="${POSTGRES_USER:-cartridge_user}"

echo "Restoring database '$POSTGRES_DB' from '$BACKUP_FILE'..."
cat "$BACKUP_FILE" | docker compose -f "$ROOT_DIR/docker-compose.yml" exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"

echo "Restore completed."
