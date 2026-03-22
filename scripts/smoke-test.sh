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
STATUS=0
MAX_ATTEMPTS=15
SLEEP_SECONDS=2

check_get() {
  local path="$1"
  local code=""
  local attempt
  for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
    code="$(curl -s -o /tmp/smoke-body.json -w '%{http_code}' "${BASE_URL}${path}" || true)"
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
    code="$(curl -s -o /tmp/smoke-front-body.out -w '%{http_code}' "${FRONTEND_URL}${path}" || true)"
    if [[ "$code" == "200" ]]; then
      echo "  OK  GET ${FRONTEND_URL}${path} -> 200"
      return
    fi
    sleep "$SLEEP_SECONDS"
  done
  echo "  FAIL GET ${FRONTEND_URL}${path} -> ${code}"
  STATUS=1
}

echo "== Cartridge System Smoke Test =="
echo "Base URL: $BASE_URL"
echo "Frontend URL: $FRONTEND_URL"
echo

check_get_frontend ""
check_get_frontend "/api/departments"
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
