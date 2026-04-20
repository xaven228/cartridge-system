#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "$ROOT_DIR/.env" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
fi

APP_PORT="${APP_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
BASE_URL="http://localhost:${APP_PORT}"
FRONTEND_URL="http://localhost:${FRONTEND_PORT}"
APP_HEALTHCHECK_USERNAME="${APP_HEALTHCHECK_USERNAME:-admin}"
APP_HEALTHCHECK_PASSWORD="${APP_HEALTHCHECK_PASSWORD:-00000}"
STATUS=0
MAX_ATTEMPTS=15
SLEEP_SECONDS=2
BACKEND_TOKEN=""
FRONTEND_TOKEN=""

extract_token() {
  sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$1" | head -n 1
}

check_get() {
  local path="$1"
  local code=""
  local attempt
  for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
    code="$(curl -s -o /tmp/smoke-body.json -w '%{http_code}' \
      -H "Authorization: Bearer ${BACKEND_TOKEN}" \
      "${BASE_URL}${path}" || true)"
    if [[ "$code" == "200" ]]; then
      echo "  OK  GET ${path} -> 200"
      return
    fi
    sleep "$SLEEP_SECONDS"
  done
  echo "  FAIL GET ${path} -> ${code}"
  STATUS=1
}

check_get_frontend() {
  local path="$1"
  local code=""
  local attempt
  for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
    code="$(curl -s -o /tmp/smoke-front-body.out -w '%{http_code}' \
      -H "Authorization: Bearer ${FRONTEND_TOKEN}" \
      "${FRONTEND_URL}${path}" || true)"
    if [[ "$code" == "200" ]]; then
      echo "  OK  GET ${FRONTEND_URL}${path} -> 200"
      return
    fi
    sleep "$SLEEP_SECONDS"
  done
  echo "  FAIL GET ${FRONTEND_URL}${path} -> ${code}"
  STATUS=1
}

login_backend() {
  local code=""
  code="$(curl -s -o /tmp/smoke-login-backend.json -w '%{http_code}' \
    -X POST "${BASE_URL}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${APP_HEALTHCHECK_USERNAME}\",\"password\":\"${APP_HEALTHCHECK_PASSWORD}\"}" || true)"
  BACKEND_TOKEN="$(extract_token /tmp/smoke-login-backend.json)"
  if [[ "$code" == "200" && -n "$BACKEND_TOKEN" ]]; then
    echo "  OK  POST /api/auth/login -> 200"
  else
    echo "  FAIL POST /api/auth/login -> ${code}"
    STATUS=1
  fi
}

login_frontend() {
  local code=""
  code="$(curl -s -o /tmp/smoke-login-frontend.json -w '%{http_code}' \
    -X POST "${FRONTEND_URL}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${APP_HEALTHCHECK_USERNAME}\",\"password\":\"${APP_HEALTHCHECK_PASSWORD}\"}" || true)"
  FRONTEND_TOKEN="$(extract_token /tmp/smoke-login-frontend.json)"
  if [[ "$code" == "200" && -n "$FRONTEND_TOKEN" ]]; then
    echo "  OK  POST ${FRONTEND_URL}/api/auth/login -> 200"
  else
    echo "  FAIL POST ${FRONTEND_URL}/api/auth/login -> ${code}"
    STATUS=1
  fi
}

echo "== Cartridge System Smoke Test =="
echo "Base URL: $BASE_URL"
echo "Frontend URL: $FRONTEND_URL"
echo

login_frontend
check_get_frontend ""
check_get_frontend "/api/auth/me"
login_backend
check_get "/api/departments"
check_get "/api/cartridge-models"
check_get "/api/cartridges"
check_get "/api/refill-history/cartridge/1"

echo
if [[ "$STATUS" -eq 0 ]]; then
  echo "Result: PASS"
else
  echo "Result: FAIL"
fi

exit "$STATUS"
