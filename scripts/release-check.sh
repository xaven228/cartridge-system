#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
HEALTH_SCRIPT="$ROOT_DIR/scripts/check-health.sh"
SMOKE_SCRIPT="$ROOT_DIR/scripts/smoke-test.sh"
BACKUP_SCRIPT="$ROOT_DIR/scripts/backup-db.sh"
WITH_BACKUP=0
STATUS=0
WARNINGS=0

for arg in "$@"; do
  case "$arg" in
    --with-backup)
      WITH_BACKUP=1
      ;;
    *)
      echo "Usage: ./scripts/release-check.sh [--with-backup]"
      exit 1
      ;;
  esac
done

print_ok() {
  echo "  OK: $1"
}

print_fail() {
  echo "  FAIL: $1"
  STATUS=1
}

print_warn() {
  echo "  WARN: $1"
  WARNINGS=1
}

value_or_default() {
  local value="$1"
  local fallback="$2"
  if [[ -n "$value" ]]; then
    echo "$value"
  else
    echo "$fallback"
  fi
}

echo "== Cartridge System Release Check =="
echo

echo "[1] Environment file"
if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1091
  source "$ENV_FILE"
  print_ok ".env found"
else
  print_fail ".env not found. Create it from .env.example before release."
fi
echo

POSTGRES_PASSWORD="$(value_or_default "${POSTGRES_PASSWORD:-}" "cartridge_pass")"
APP_JWT_SECRET="$(value_or_default "${APP_JWT_SECRET:-}" "change-this-secret-before-production-change-this-secret-before-production")"
POSTGRES_HOST_BIND="$(value_or_default "${POSTGRES_HOST_BIND:-}" "127.0.0.1")"
POSTGRES_HOST_PORT="$(value_or_default "${POSTGRES_HOST_PORT:-}" "5433")"
APP_HOST_BIND="$(value_or_default "${APP_HOST_BIND:-}" "127.0.0.1")"
APP_PORT="$(value_or_default "${APP_PORT:-}" "8080")"
FRONTEND_HOST_BIND="$(value_or_default "${FRONTEND_HOST_BIND:-}" "0.0.0.0")"
FRONTEND_PORT="$(value_or_default "${FRONTEND_PORT:-}" "3000")"
APP_HEALTHCHECK_USERNAME="$(value_or_default "${APP_HEALTHCHECK_USERNAME:-}" "admin")"
APP_HEALTHCHECK_PASSWORD="$(value_or_default "${APP_HEALTHCHECK_PASSWORD:-}" "00000")"

echo "[2] Sensitive configuration"
if [[ "$POSTGRES_PASSWORD" == "cartridge_pass" ]]; then
  print_fail "POSTGRES_PASSWORD still uses the default value."
else
  print_ok "POSTGRES_PASSWORD is customized"
fi

if [[ "$APP_JWT_SECRET" == "change-this-secret-before-production-change-this-secret-before-production" ]]; then
  print_fail "APP_JWT_SECRET still uses the default placeholder."
else
  print_ok "APP_JWT_SECRET is customized"
fi

if [[ "$APP_HEALTHCHECK_USERNAME" == "admin" && "$APP_HEALTHCHECK_PASSWORD" == "00000" ]]; then
  print_fail "Health-check credentials still use the default admin password."
elif [[ -z "$APP_HEALTHCHECK_PASSWORD" ]]; then
  print_fail "APP_HEALTHCHECK_PASSWORD is empty."
else
  print_ok "Health-check credentials are customized"
fi

if [[ "$APP_HOST_BIND" == "127.0.0.1" ]]; then
  print_ok "backend is bound to 127.0.0.1"
else
  print_warn "backend is exposed on ${APP_HOST_BIND}:${APP_PORT}"
fi

if [[ "$POSTGRES_HOST_BIND" == "127.0.0.1" ]]; then
  print_ok "postgres is bound to 127.0.0.1"
else
  print_warn "postgres is exposed on ${POSTGRES_HOST_BIND}:${POSTGRES_HOST_PORT}"
fi

if [[ "$FRONTEND_HOST_BIND" == "0.0.0.0" ]]; then
  print_ok "frontend is published externally"
else
  print_warn "frontend is bound to ${FRONTEND_HOST_BIND}:${FRONTEND_PORT}"
fi
echo

echo "[3] Compose validation"
if docker compose -f "$COMPOSE_FILE" config >/tmp/cartridge-release-compose.yml 2>/dev/null; then
  print_ok "docker-compose.yml is valid"
else
  print_fail "docker-compose.yml validation failed"
fi
echo

echo "[4] Running containers"
if docker compose -f "$COMPOSE_FILE" ps postgres --status running | grep -q postgres; then
  print_ok "postgres is running"
else
  print_fail "postgres is not running"
fi

if docker compose -f "$COMPOSE_FILE" ps backend --status running | grep -q backend; then
  print_ok "backend is running"
else
  print_fail "backend is not running"
fi

if docker compose -f "$COMPOSE_FILE" ps frontend --status running | grep -q frontend; then
  print_ok "frontend is running"
else
  print_fail "frontend is not running"
fi
echo

echo "[5] Port exposure"
if ss -ltn | grep -q "127.0.0.1:${APP_PORT} "; then
  print_ok "backend listens on 127.0.0.1:${APP_PORT}"
elif ss -ltn | grep -q ":${APP_PORT} "; then
  print_warn "backend is listening, but not only on 127.0.0.1:${APP_PORT}"
else
  print_fail "backend port ${APP_PORT} is not listening"
fi

if ss -ltn | grep -q "127.0.0.1:${POSTGRES_HOST_PORT} "; then
  print_ok "postgres listens on 127.0.0.1:${POSTGRES_HOST_PORT}"
elif ss -ltn | grep -q ":${POSTGRES_HOST_PORT} "; then
  print_warn "postgres is listening, but not only on 127.0.0.1:${POSTGRES_HOST_PORT}"
else
  print_fail "postgres port ${POSTGRES_HOST_PORT} is not listening"
fi

if ss -ltn | grep -q ":${FRONTEND_PORT} "; then
  print_ok "frontend port ${FRONTEND_PORT} is listening"
else
  print_fail "frontend port ${FRONTEND_PORT} is not listening"
fi
echo

echo "[6] HTTP headers and health"
FRONT_HEADERS="$(curl -I -s "http://localhost:${FRONTEND_PORT}" || true)"
if grep -q "HTTP/1.1 200" <<<"$FRONT_HEADERS"; then
  print_ok "frontend returns HTTP 200"
else
  print_fail "frontend does not return HTTP 200"
fi

for header in "X-Content-Type-Options" "X-Frame-Options" "Referrer-Policy" "Permissions-Policy"; do
  if grep -qi "^${header}:" <<<"$FRONT_HEADERS"; then
    print_ok "${header} header is present"
  else
    print_warn "${header} header is missing"
  fi
done

if "$HEALTH_SCRIPT"; then
  print_ok "health-check passed"
else
  print_fail "health-check failed"
fi

if "$SMOKE_SCRIPT"; then
  print_ok "smoke-test passed"
else
  print_fail "smoke-test failed"
fi
echo

echo "[7] Backup readiness"
if crontab -l 2>/dev/null | grep -q "./scripts/backup-db.sh"; then
  print_ok "backup cron job is installed"
else
  print_warn "backup cron job is not installed"
fi

if [[ "$WITH_BACKUP" -eq 1 ]]; then
  if "$BACKUP_SCRIPT" >/tmp/cartridge-release-backup.out 2>&1; then
    print_ok "backup script completed successfully"
  else
    cat /tmp/cartridge-release-backup.out
    print_fail "backup script failed"
  fi
else
  print_warn "backup execution skipped. Run with --with-backup to verify backup end-to-end."
fi
echo

if [[ "$STATUS" -eq 0 && "$WARNINGS" -eq 0 ]]; then
  echo "Result: READY FOR RELEASE"
elif [[ "$STATUS" -eq 0 ]]; then
  echo "Result: READY WITH WARNINGS"
else
  echo "Result: NOT READY"
fi

exit "$STATUS"
