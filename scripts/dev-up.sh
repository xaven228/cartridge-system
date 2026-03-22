#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "$ROOT_DIR/.env" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
fi

APP_PORT="${APP_PORT:-8080}"

echo "[1/2] Starting containers (postgres + backend)..."
docker compose -f "$ROOT_DIR/docker-compose.yml" up -d --build

echo "[2/2] Waiting for API on port $APP_PORT..."
for _ in {1..60}; do
  if curl -s -o /dev/null "http://localhost:${APP_PORT}/api/departments"; then
    echo "Backend is up: http://localhost:${APP_PORT}"
    exit 0
  fi
  sleep 1
done

echo "Backend did not become ready in time. Check logs:"
echo "  docker compose -f \"$ROOT_DIR/docker-compose.yml\" logs backend"
exit 1
