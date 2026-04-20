#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/logs"
LOG_FILE="$LOG_DIR/backup-cron.log"
HOUR="${1:-2}"
MINUTE="${2:-0}"

if ! [[ "$HOUR" =~ ^[0-9]+$ ]] || ! [[ "$MINUTE" =~ ^[0-9]+$ ]]; then
  echo "Usage: ./scripts/install-backup-cron.sh [hour] [minute]"
  exit 1
fi

if (( HOUR < 0 || HOUR > 23 || MINUTE < 0 || MINUTE > 59 )); then
  echo "Hour must be 0..23 and minute must be 0..59."
  exit 1
fi

mkdir -p "$LOG_DIR"

CRON_MARKER="# cartridge-system backup job"
CRON_COMMAND="${MINUTE} ${HOUR} * * * cd \"$ROOT_DIR\" && ./scripts/backup-db.sh >> \"$LOG_FILE\" 2>&1"
CURRENT_CRONTAB="$(crontab -l 2>/dev/null || true)"

UPDATED_CRONTAB="$(
  printf '%s\n' "$CURRENT_CRONTAB" | awk '
    !/cartridge-system backup job/ && !/\.\/scripts\/backup-db\.sh/ { print }
  '
)"

{
  printf '%s\n' "$UPDATED_CRONTAB"
  printf '%s\n' "$CRON_MARKER"
  printf '%s\n' "$CRON_COMMAND"
} | sed '/^[[:space:]]*$/N;/^\n$/D' | crontab -

echo "Daily backup cron installed."
echo "Time: $(printf '%02d:%02d' "$HOUR" "$MINUTE")"
echo "Log file: $LOG_FILE"
echo
echo "Current matching crontab lines:"
crontab -l | grep -n "cartridge-system backup job\\|backup-db.sh" || true
