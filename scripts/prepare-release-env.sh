#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
CRON_SCRIPT="$ROOT_DIR/scripts/install-backup-cron.sh"
WRITE=0
ROTATE_ADMIN=0
INSTALL_BACKUP_CRON=0
BACKUP_HOUR=2
BACKUP_MINUTE=0
ADMIN_PASSWORD=""
CURRENT_ADMIN_USERNAME=""
CURRENT_ADMIN_PASSWORD=""
ENV_BACKUP=""
SUCCESS=0
DB_ROTATED=0
ADMIN_ROTATED=0

POSTGRES_DB=""
POSTGRES_USER=""
OLD_POSTGRES_PASSWORD=""
NEW_POSTGRES_PASSWORD=""
NEW_JWT_SECRET=""
APP_PORT=""
HEALTHCHECK_USERNAME=""
HEALTHCHECK_PASSWORD=""
OLD_ADMIN_PASSWORD=""
NEW_ADMIN_PASSWORD=""
AUTH_TOKEN=""
ADMIN_ME_FILE=""
ADMIN_USER_ID=""

usage() {
  cat <<'EOF'
Usage:
  ./scripts/prepare-release-env.sh [options]

Options:
  --write                     apply changes to .env and runtime
  --rotate-admin-password     change admin password through API and update APP_HEALTHCHECK_PASSWORD
  --admin-password VALUE      use this admin password instead of generating one
  --current-admin-username U  current username for admin login (default: APP_HEALTHCHECK_USERNAME or admin)
  --current-admin-password P  current password for admin login (default: APP_HEALTHCHECK_PASSWORD or 00000)
  --install-backup-cron       install daily backup cron after successful write
  --backup-hour H             hour for backup cron, default 2
  --backup-minute M           minute for backup cron, default 0

Default mode is dry-run: nothing is changed until --write is passed.
EOF
}

print_step() {
  echo
  echo "[$1] $2"
}

require_env_file() {
  if [[ ! -f "$ENV_FILE" ]]; then
    echo ".env not found at $ENV_FILE"
    exit 1
  fi
}

load_env() {
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  POSTGRES_DB="${POSTGRES_DB:-cartridge_db}"
  POSTGRES_USER="${POSTGRES_USER:-cartridge_user}"
  OLD_POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-cartridge_pass}"
  APP_PORT="${APP_PORT:-8080}"
  HEALTHCHECK_USERNAME="${APP_HEALTHCHECK_USERNAME:-admin}"
  HEALTHCHECK_PASSWORD="${APP_HEALTHCHECK_PASSWORD:-00000}"
}

generate_hex_secret() {
  local bytes="$1"
  openssl rand -hex "$bytes"
}

json_escape() {
  python3 - "$1" <<'PY'
import json
import sys
print(json.dumps(sys.argv[1]))
PY
}

extract_json_value() {
  local file="$1"
  local path="$2"
  python3 - "$file" "$path" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fh:
    data = json.load(fh)

value = data
for part in sys.argv[2].split("."):
    value = value[part]

if isinstance(value, bool):
    print("true" if value else "false")
elif value is None:
    print("")
else:
    print(value)
PY
}

upsert_env_value() {
  local key="$1"
  local value="$2"
  python3 - "$ENV_FILE" "$key" "$value" <<'PY'
from pathlib import Path
import sys

env_path = Path(sys.argv[1])
key = sys.argv[2]
value = sys.argv[3]

lines = env_path.read_text(encoding="utf-8").splitlines()
prefix = f"{key}="
updated = False
new_lines = []

for line in lines:
    if line.startswith(prefix):
        new_lines.append(prefix + value)
        updated = True
    else:
        new_lines.append(line)

if not updated:
    new_lines.append(prefix + value)

env_path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")
PY
}

alter_postgres_password() {
  local password="$1"
  docker compose -f "$COMPOSE_FILE" exec -T postgres \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -c "ALTER USER \"$POSTGRES_USER\" WITH PASSWORD '$password';" >/dev/null
}

login_admin() {
  local username="$1"
  local password="$2"
  local login_file
  login_file="$(mktemp)"
  local login_payload
  login_payload="$(python3 - "$username" "$password" <<'PY'
import json
import sys
print(json.dumps({"username": sys.argv[1], "password": sys.argv[2]}, separators=(",", ":")))
PY
)"
  local code
  code="$(curl -s -o "$login_file" -w '%{http_code}' \
    -X POST "http://localhost:${APP_PORT}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "$login_payload" || true)"
  if [[ "$code" != "200" ]]; then
    echo "Не удалось войти под текущим админом. HTTP $code"
    rm -f "$login_file"
    return 1
  fi

  AUTH_TOKEN="$(extract_json_value "$login_file" "token")"
  ADMIN_ME_FILE="$(mktemp)"
  code="$(curl -s -o "$ADMIN_ME_FILE" -w '%{http_code}' \
    "http://localhost:${APP_PORT}/api/auth/me" \
    -H "Authorization: Bearer ${AUTH_TOKEN}" || true)"
  rm -f "$login_file"
  if [[ "$code" != "200" ]]; then
    echo "Не удалось получить /api/auth/me. HTTP $code"
    return 1
  fi

  ADMIN_USER_ID="$(extract_json_value "$ADMIN_ME_FILE" "id")"
}

update_admin_password() {
  local password="$1"
  local payload_file
  payload_file="$(mktemp)"
  python3 - "$ADMIN_ME_FILE" "$password" >"$payload_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fh:
    user = json.load(fh)

payload = {
    "username": user["username"],
    "fullName": user["fullName"],
    "password": sys.argv[2],
    "role": user["role"],
    "active": user["active"],
    "permissions": user["permissions"],
}

print(json.dumps(payload, separators=(",", ":")))
PY
  local code
  code="$(curl -s -o /tmp/cartridge-admin-update.json -w '%{http_code}' \
    -X PUT "http://localhost:${APP_PORT}/api/users/${ADMIN_USER_ID}" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${AUTH_TOKEN}" \
    -d @"$payload_file" || true)"
  rm -f "$payload_file"
  [[ "$code" == "200" ]]
}

cleanup_on_error() {
  local exit_code="$1"
  if [[ "$SUCCESS" -eq 1 ]]; then
    rm -f "$ADMIN_ME_FILE"
    return
  fi

  if [[ "$ADMIN_ROTATED" -eq 1 && -n "$OLD_ADMIN_PASSWORD" && -n "$AUTH_TOKEN" && -n "$ADMIN_ME_FILE" ]]; then
    update_admin_password "$OLD_ADMIN_PASSWORD" >/dev/null 2>&1 || true
  fi

  if [[ "$DB_ROTATED" -eq 1 && -n "$OLD_POSTGRES_PASSWORD" ]]; then
    alter_postgres_password "$OLD_POSTGRES_PASSWORD" >/dev/null 2>&1 || true
  fi

  if [[ -n "$ENV_BACKUP" && -f "$ENV_BACKUP" ]]; then
    cp "$ENV_BACKUP" "$ENV_FILE" >/dev/null 2>&1 || true
  fi

  rm -f "$ADMIN_ME_FILE"
  exit "$exit_code"
}

trap 'cleanup_on_error $?' ERR

while [[ $# -gt 0 ]]; do
  case "$1" in
    --write)
      WRITE=1
      shift
      ;;
    --rotate-admin-password)
      ROTATE_ADMIN=1
      shift
      ;;
    --admin-password)
      ADMIN_PASSWORD="${2:-}"
      shift 2
      ;;
    --current-admin-username)
      CURRENT_ADMIN_USERNAME="${2:-}"
      shift 2
      ;;
    --current-admin-password)
      CURRENT_ADMIN_PASSWORD="${2:-}"
      shift 2
      ;;
    --install-backup-cron)
      INSTALL_BACKUP_CRON=1
      shift
      ;;
    --backup-hour)
      BACKUP_HOUR="${2:-}"
      shift 2
      ;;
    --backup-minute)
      BACKUP_MINUTE="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
done

require_env_file
load_env

CURRENT_ADMIN_USERNAME="${CURRENT_ADMIN_USERNAME:-$HEALTHCHECK_USERNAME}"
CURRENT_ADMIN_PASSWORD="${CURRENT_ADMIN_PASSWORD:-$HEALTHCHECK_PASSWORD}"
OLD_ADMIN_PASSWORD="$CURRENT_ADMIN_PASSWORD"
NEW_POSTGRES_PASSWORD="$(generate_hex_secret 16)"
NEW_JWT_SECRET="$(generate_hex_secret 32)"

if [[ "$ROTATE_ADMIN" -eq 1 ]]; then
  NEW_ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(generate_hex_secret 16)}"
fi

print_step "1" "План изменений"
echo "  .env файл: $ENV_FILE"
echo "  Ротация POSTGRES_PASSWORD: yes"
echo "  Ротация APP_JWT_SECRET: yes"
if [[ "$ROTATE_ADMIN" -eq 1 ]]; then
  echo "  Ротация admin-пароля через API: yes"
else
  echo "  Ротация admin-пароля через API: no"
fi
if [[ "$INSTALL_BACKUP_CRON" -eq 1 ]]; then
  echo "  Установка backup cron: yes (${BACKUP_HOUR}:${BACKUP_MINUTE})"
else
  echo "  Установка backup cron: no"
fi

if [[ "$WRITE" -eq 0 ]]; then
  print_step "2" "Dry-run"
  echo "  Ничего не изменено. Для применения используй --write."
  if [[ "$ROTATE_ADMIN" -eq 1 ]]; then
    echo "  Новый admin-пароль будет сгенерирован и записан в APP_HEALTHCHECK_PASSWORD."
  fi
  SUCCESS=1
  exit 0
fi

print_step "2" "Проверка текущего доступа"
if [[ "$ROTATE_ADMIN" -eq 1 ]]; then
  login_admin "$CURRENT_ADMIN_USERNAME" "$CURRENT_ADMIN_PASSWORD"
  echo "  OK: текущий admin-доступ подтверждён"
else
  echo "  Пропущено: admin-пароль не вращается"
fi

print_step "3" "Резервная копия .env"
ENV_BACKUP="$ROOT_DIR/.env.bak.$(date +%Y%m%d-%H%M%S)"
cp "$ENV_FILE" "$ENV_BACKUP"
echo "  Backup: $ENV_BACKUP"

print_step "4" "Ротация PostgreSQL-пароля"
alter_postgres_password "$NEW_POSTGRES_PASSWORD"
DB_ROTATED=1
echo "  OK: пароль пользователя БД обновлён"

print_step "5" "Обновление .env"
upsert_env_value "POSTGRES_PASSWORD" "$NEW_POSTGRES_PASSWORD"
upsert_env_value "APP_JWT_SECRET" "$NEW_JWT_SECRET"
if [[ "$ROTATE_ADMIN" -eq 1 ]]; then
  upsert_env_value "APP_HEALTHCHECK_USERNAME" "$CURRENT_ADMIN_USERNAME"
fi
echo "  OK: .env обновлён"

if [[ "$ROTATE_ADMIN" -eq 1 ]]; then
  print_step "6" "Ротация admin-пароля"
  if update_admin_password "$NEW_ADMIN_PASSWORD"; then
    ADMIN_ROTATED=1
    upsert_env_value "APP_HEALTHCHECK_PASSWORD" "$NEW_ADMIN_PASSWORD"
    echo "  OK: admin-пароль обновлён через API"
  else
    echo "  Не удалось обновить admin-пароль через API"
    exit 1
  fi
else
  print_step "6" "Ротация admin-пароля"
  echo "  Пропущено"
fi

if [[ "$INSTALL_BACKUP_CRON" -eq 1 ]]; then
  print_step "7" "Установка backup cron"
  "$CRON_SCRIPT" "$BACKUP_HOUR" "$BACKUP_MINUTE"
else
  print_step "7" "Установка backup cron"
  echo "  Пропущено"
fi

SUCCESS=1
rm -f "$ADMIN_ME_FILE"

print_step "8" "Готово"
echo "  Секреты сохранены в .env"
echo "  JWT-сессии после перезапуска станут недействительными, это ожидаемо"
echo "  Для применения новых значений перезапусти контейнеры:"
echo "    docker compose up -d --build"
if [[ "$ROTATE_ADMIN" -eq 1 ]]; then
  echo
  echo "  Новый admin-пароль:"
  echo "    $NEW_ADMIN_PASSWORD"
fi
