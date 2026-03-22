#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
HEALTH_SCRIPT="$ROOT_DIR/scripts/check-health.sh"
SMOKE_SCRIPT="$ROOT_DIR/scripts/smoke-test.sh"
APP_IMAGE="cartridge-system-backend:latest"
FRONTEND_IMAGE="cartridge-system-frontend:latest"

PREV_IMAGE_ID="$(docker image inspect -f '{{.Id}}' "$APP_IMAGE" 2>/dev/null || true)"
PREV_FRONTEND_IMAGE_ID="$(docker image inspect -f '{{.Id}}' "$FRONTEND_IMAGE" 2>/dev/null || true)"

echo "[1/4] Building and starting updated containers..."
docker compose -f "$COMPOSE_FILE" up -d --build

echo "[2/4] Running health check..."
if "$HEALTH_SCRIPT" && "$SMOKE_SCRIPT"; then
  echo "[3/4] Update successful."
  exit 0
fi

echo "[3/4] Health check failed after update."

if [[ -z "$PREV_IMAGE_ID" || -z "$PREV_FRONTEND_IMAGE_ID" ]]; then
  echo "[4/4] Rollback not possible: previous backend/frontend image not found."
  exit 1
fi

echo "[4/4] Rolling back backend and frontend images to previous version..."
docker tag "$PREV_IMAGE_ID" "$APP_IMAGE"
docker tag "$PREV_FRONTEND_IMAGE_ID" "$FRONTEND_IMAGE"
docker compose -f "$COMPOSE_FILE" up -d --no-build --force-recreate backend frontend postgres

echo "Running health check after rollback..."
if "$HEALTH_SCRIPT" && "$SMOKE_SCRIPT"; then
  echo "Rollback successful."
else
  echo "Rollback attempted but health check is still failing."
fi

exit 1
