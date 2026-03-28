#!/usr/bin/env bash
set -euo pipefail

echo "[Spring Prism] Starting Enterprise Lab Sandbox..."
docker compose -f prism-examples/demo-app/compose.yaml up --build -d
echo "[Spring Prism] Lab is warming up."
echo "[Spring Prism] UI: http://localhost:8080"
echo "[Spring Prism] Grafana: http://localhost:3000"
