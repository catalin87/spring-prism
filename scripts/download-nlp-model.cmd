@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "MODEL_DIR=%ROOT_DIR%\prism-benchmarks\models"
if not "%~1"=="" set "MODEL_DIR=%~1"
set "MODEL_FILE=%MODEL_DIR%\en-ner-person.bin"
set "MODEL_URL=https://downloads.sourceforge.net/project/opennlp/models-1.5/en-ner-person.bin"

if not exist "%MODEL_DIR%" mkdir "%MODEL_DIR%"

echo [Spring Prism] Downloading OpenNLP person-name model...
echo [Spring Prism] Target: %MODEL_FILE%

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing -Uri '%MODEL_URL%' -OutFile '%MODEL_FILE%'"

if errorlevel 1 (
  echo [Spring Prism] Model download failed.
  exit /b 1
)

echo [Spring Prism] Model ready.
echo [Spring Prism] Benchmark property:
echo   -Dprism.bench.nlpModel=%MODEL_FILE%
