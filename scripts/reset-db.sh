#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"

echo "[1/3] Stopping containers and removing database volume..."
docker compose -f "$COMPOSE_FILE" down -v

echo "[2/3] Starting fresh stack..."
docker compose -f "$COMPOSE_FILE" up -d --build

echo "[3/3] Waiting for API..."
for _ in {1..60}; do
  if curl -s -o /dev/null "http://localhost:${APP_PORT:-8080}/api/departments"; then
    echo "Database reset complete. API is ready."
    exit 0
  fi
  sleep 1
done

echo "API did not become ready in time. Check logs:"
echo "  docker compose -f \"$COMPOSE_FILE\" logs backend"
exit 1
