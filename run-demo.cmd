@echo off
setlocal
echo [Spring Prism] Starting Enterprise Lab Sandbox...
docker compose -f prism-examples/demo-app/compose.yaml up --build -d
if errorlevel 1 (
  echo [Spring Prism] Failed to start the Enterprise Lab Sandbox.
  exit /b 1
)
echo [Spring Prism] Lab is warming up.
echo [Spring Prism] UI: http://localhost:8080
echo [Spring Prism] Grafana: http://localhost:3000
endlocal
