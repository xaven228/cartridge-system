#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"

POSTGRES_DB="${POSTGRES_DB:-cartridge_db}"
POSTGRES_USER="${POSTGRES_USER:-cartridge_user}"

# BCrypt hash for the literal password "00000".
DEFAULT_BCRYPT_HASH='$2y$10$SG1rmvjRNlO1uO3GIdzhTOqHiCV9GFil4XuylZVB9j8uM4Gq.Uwbu'

echo "Resetting password for user 'admin' in DB '$POSTGRES_DB'..."
docker compose -f "$COMPOSE_FILE" exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<SQL
UPDATE app_users
SET password_hash = '$DEFAULT_BCRYPT_HASH'
WHERE LOWER(username) = 'admin';
SQL

echo "Done. Login: admin / 00000"
