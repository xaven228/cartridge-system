#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "Stopping and removing containers..."
docker compose -f "$ROOT_DIR/docker-compose.yml" down
echo "Done."
