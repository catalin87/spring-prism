#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_DIR="${1:-$ROOT_DIR/prism-benchmarks/models}"
MODEL_FILE="$MODEL_DIR/en-ner-person.bin"
MODEL_URL="https://downloads.sourceforge.net/project/opennlp/models-1.5/en-ner-person.bin"

mkdir -p "$MODEL_DIR"

echo "[Spring Prism] Downloading OpenNLP person-name model..."
echo "[Spring Prism] Target: $MODEL_FILE"

curl -fL "$MODEL_URL" -o "$MODEL_FILE"

echo "[Spring Prism] Model ready."
echo "[Spring Prism] Benchmark property:"
echo "  -Dprism.bench.nlpModel=$MODEL_FILE"
