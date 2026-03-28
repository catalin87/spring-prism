@echo off
setlocal
echo [Spring Prism] Stopping Enterprise Lab Sandbox...
docker compose -f prism-examples/demo-app/compose.yaml down
endlocal
