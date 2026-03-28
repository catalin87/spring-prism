#!/usr/bin/env bash
set -euo pipefail

echo "[Spring Prism] Stopping Enterprise Lab Sandbox..."
docker compose -f prism-examples/demo-app/compose.yaml down
