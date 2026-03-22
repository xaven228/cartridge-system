#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-cartridge-system}"
VOLUME_NAME="${PROJECT_NAME}_postgres_data"
BACKEND_IMAGE="cartridge-system-backend:latest"

echo "== Cartridge System Status =="
echo

echo "[1] Containers"
docker compose -f "$COMPOSE_FILE" ps || true
echo

echo "[2] Backend image"
if docker image inspect "$BACKEND_IMAGE" >/dev/null 2>&1; then
  IMAGE_ID="$(docker image inspect -f '{{.Id}}' "$BACKEND_IMAGE")"
  IMAGE_CREATED="$(docker image inspect -f '{{.Created}}' "$BACKEND_IMAGE")"
  echo "  Image: $BACKEND_IMAGE"
  echo "  Id: $IMAGE_ID"
  echo "  Created: $IMAGE_CREATED"
else
  echo "  Image '$BACKEND_IMAGE' not found."
fi
echo

echo "[3] Postgres volume size"
if docker volume inspect "$VOLUME_NAME" >/dev/null 2>&1; then
  docker run --rm -v "${VOLUME_NAME}:/data:ro" alpine:3.20 sh -lc "du -sh /data 2>/dev/null || true" | sed 's/^/  /'
else
  echo "  Volume '$VOLUME_NAME' not found."
fi
echo

echo "[4] Recent backend errors (last 200 lines)"
if docker compose -f "$COMPOSE_FILE" ps backend --status running | grep -q backend; then
  docker compose -f "$COMPOSE_FILE" logs --tail 200 backend 2>/dev/null | rg -n "ERROR|Exception|Caused by" || echo "  No ERROR/Exception lines found."
else
  echo "  Backend container is not running."
fi
