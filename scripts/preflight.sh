#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
STATUS=0

echo "== Cartridge System Preflight =="
echo

echo "[1] Docker CLI"
if command -v docker >/dev/null 2>&1; then
  docker --version | sed 's/^/  /'
else
  echo "  FAIL: docker command not found"
  STATUS=1
fi
echo

echo "[2] Docker daemon"
if docker info >/dev/null 2>&1; then
  echo "  OK: Docker daemon is reachable"
else
  echo "  FAIL: Docker daemon is not reachable"
  STATUS=1
fi
echo

echo "[3] Docker Compose"
if docker compose version >/dev/null 2>&1; then
  docker compose version | sed 's/^/  /'
else
  echo "  FAIL: docker compose is not available"
  STATUS=1
fi
echo

echo "[4] Environment file"
if [[ -f "$ENV_FILE" ]]; then
  echo "  OK: .env found at $ENV_FILE"
  # shellcheck disable=SC1091
  source "$ENV_FILE"
else
  echo "  FAIL: .env not found (create from .env.example)"
  STATUS=1
fi
echo

APP_PORT="${APP_PORT:-8080}"
POSTGRES_HOST_PORT="${POSTGRES_HOST_PORT:-5433}"

echo "[5] Host ports currently in use"
if ss -ltn | grep -q ":${APP_PORT} "; then
  echo "  WARN: APP_PORT ${APP_PORT} is already in use"
else
  echo "  OK: APP_PORT ${APP_PORT} is free"
fi

if ss -ltn | grep -q ":${POSTGRES_HOST_PORT} "; then
  echo "  WARN: POSTGRES_HOST_PORT ${POSTGRES_HOST_PORT} is already in use"
else
  echo "  OK: POSTGRES_HOST_PORT ${POSTGRES_HOST_PORT} is free"
fi
echo

echo "[6] Compose config validation"
if docker compose -f "$ROOT_DIR/docker-compose.yml" config >/dev/null 2>&1; then
  echo "  OK: docker-compose.yml is valid"
else
  echo "  FAIL: docker-compose.yml validation failed"
  STATUS=1
fi

echo
if [[ "$STATUS" -eq 0 ]]; then
  echo "Result: READY"
else
  echo "Result: NOT READY"
fi

exit "$STATUS"
