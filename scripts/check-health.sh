#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "$ROOT_DIR/.env" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
fi

APP_PORT="${APP_PORT:-8080}"
POSTGRES_HOST_PORT="${POSTGRES_HOST_PORT:-5433}"

echo "== Cartridge System Health Check =="

STATUS=0

echo
echo "[1] Docker containers"
if docker compose -f "$ROOT_DIR/docker-compose.yml" ps --format json >/tmp/cartridge_ps.json 2>/dev/null; then
  cat /tmp/cartridge_ps.json | sed 's/^/  /'
else
  echo "  Failed to read docker compose status."
  STATUS=1
fi

echo
echo "[2] API check"
HTTP_CODE="$(curl -s -o /tmp/cartridge_api_body.json -w '%{http_code}' "http://localhost:${APP_PORT}/api/departments" || true)"
if [[ "$HTTP_CODE" == "200" ]]; then
  echo "  OK: API responds on port $APP_PORT (HTTP 200)"
else
  echo "  FAIL: API check on port $APP_PORT returned HTTP $HTTP_CODE"
  STATUS=1
fi

echo
echo "[3] TCP ports"
if ss -ltn | grep -q ":${APP_PORT} "; then
  echo "  OK: app port ${APP_PORT} is listening"
else
  echo "  FAIL: app port ${APP_PORT} is not listening"
  STATUS=1
fi

if ss -ltn | grep -q ":${POSTGRES_HOST_PORT} "; then
  echo "  OK: postgres host port ${POSTGRES_HOST_PORT} is listening"
else
  echo "  FAIL: postgres host port ${POSTGRES_HOST_PORT} is not listening"
  STATUS=1
fi

echo
echo "[4] Disk usage"
df -h "$ROOT_DIR" | sed 's/^/  /'

if [[ "$STATUS" -eq 0 ]]; then
  echo
  echo "Result: HEALTHY"
else
  echo
  echo "Result: UNHEALTHY"
fi

exit "$STATUS"
