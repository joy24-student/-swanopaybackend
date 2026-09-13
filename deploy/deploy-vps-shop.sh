#!/usr/bin/env bash
# Prepare the hosting platform once; individual stores launch through the API/app.
set -euo pipefail
task_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
task_compose="$task_root/deploy/storefronts/compose.yml"
task_env="$task_root/deploy/storefronts/.env"
if ! command -v docker >/dev/null || ! docker compose version >/dev/null 2>&1; then
  echo "Docker Engine and Docker Compose v2 are required. See deploy/storefronts/README.md." >&2
  exit 1
fi
if [ ! -f "$task_env" ]; then
  echo "Create deploy/storefronts/.env from .env.example and complete the hosting settings first." >&2
  exit 1
fi
# Validate without printing the rendered configuration (it contains credentials).
docker compose --env-file "$task_env" -f "$task_compose" config --quiet
docker compose --env-file "$task_env" -f "$task_compose" build
docker compose --env-file "$task_env" -f "$task_compose" up -d --wait --wait-timeout 180
echo "Hosting services started. Store launches are verified separately by the app and HTTPS health check."
docker compose --env-file "$task_env" -f "$task_compose" ps
