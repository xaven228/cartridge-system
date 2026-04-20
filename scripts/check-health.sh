#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "$ROOT_DIR/.env" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
fi

APP_PORT="${APP_PORT:-8080}"
POSTGRES_HOST_PORT="${POSTGRES_HOST_PORT:-5433}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
APP_HEALTHCHECK_USERNAME="${APP_HEALTHCHECK_USERNAME:-admin}"
APP_HEALTHCHECK_PASSWORD="${APP_HEALTHCHECK_PASSWORD:-00000}"

extract_token() {
  sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$1" | head -n 1
}

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
echo "[2] Frontend check"
FRONT_HTTP_CODE="$(curl -s -o /tmp/cartridge_front_body.html -w '%{http_code}' "http://localhost:${FRONTEND_PORT}" || true)"
if [[ "$FRONT_HTTP_CODE" == "200" ]]; then
  echo "  OK: frontend responds on port $FRONTEND_PORT (HTTP 200)"
else
  echo "  FAIL: frontend check on port $FRONTEND_PORT returned HTTP $FRONT_HTTP_CODE"
  STATUS=1
fi

echo
echo "[3] API auth check"
LOGIN_CODE="$(curl -s -o /tmp/cartridge_login_body.json -w '%{http_code}' \
  -X POST "http://localhost:${APP_PORT}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${APP_HEALTHCHECK_USERNAME}\",\"password\":\"${APP_HEALTHCHECK_PASSWORD}\"}" || true)"
AUTH_TOKEN="$(extract_token /tmp/cartridge_login_body.json)"
if [[ "$LOGIN_CODE" == "200" && -n "$AUTH_TOKEN" ]]; then
  echo "  OK: login works on port $APP_PORT (HTTP 200)"
else
  echo "  FAIL: login check on port $APP_PORT returned HTTP $LOGIN_CODE"
  STATUS=1
fi

echo
echo "[4] Authenticated API check"
ME_CODE="$(curl -s -o /tmp/cartridge_me_body.json -w '%{http_code}' \
  "http://localhost:${APP_PORT}/api/auth/me" \
  -H "Authorization: Bearer ${AUTH_TOKEN}" || true)"
if [[ "$ME_CODE" == "200" ]]; then
  echo "  OK: authenticated API responds on port $APP_PORT (HTTP 200)"
else
  echo "  FAIL: authenticated API check on port $APP_PORT returned HTTP $ME_CODE"
  STATUS=1
fi

echo
echo "[5] TCP ports"
if ss -ltn | grep -q ":${FRONTEND_PORT} "; then
  echo "  OK: frontend port ${FRONTEND_PORT} is listening"
else
  echo "  FAIL: frontend port ${FRONTEND_PORT} is not listening"
  STATUS=1
fi

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
echo "[6] Disk usage"
df -h "$ROOT_DIR" | sed 's/^/  /'

if [[ "$STATUS" -eq 0 ]]; then
  echo
  echo "Result: HEALTHY"
else
  echo
  echo "Result: UNHEALTHY"
fi

exit "$STATUS"
