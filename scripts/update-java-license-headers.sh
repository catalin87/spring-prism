#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HEADER_FILE="$ROOT_DIR/license-header.txt"

if [[ ! -f "$HEADER_FILE" ]]; then
  echo "Missing header template: $HEADER_FILE" >&2
  exit 1
fi

python3 - "$ROOT_DIR" "$HEADER_FILE" <<'PY'
from pathlib import Path
import subprocess
import sys

root = Path(sys.argv[1])
header_file = Path(sys.argv[2])

template_lines = header_file.read_text(encoding="utf-8").splitlines()

header = ["/*"]
for line in template_lines:
    if line:
        header.append(f" * {line}")
    else:
        header.append(" *")
header.append(" */")
header_block = "\n".join(header) + "\n"


def strip_leading_header(text: str) -> str:
    if not text.startswith("/*"):
        return text.lstrip("\ufeff")

    end = text.find("*/")
    if end == -1:
        return text.lstrip("\ufeff")

    remainder = text[end + 2 :]
    return remainder.lstrip("\r\n")


listed_files = subprocess.run(
    ["rg", "--files", "-g", "*.java"],
    cwd=root,
    check=True,
    capture_output=True,
    text=True,
).stdout.splitlines()

java_files = [
    root / relative_path
    for relative_path in listed_files
    if "target/" not in relative_path
    and not relative_path.startswith("website/")
]

updated = 0
for path in java_files:
    original = path.read_text(encoding="utf-8")
    body = strip_leading_header(original)
    updated_text = header_block + "\n" + body.lstrip("\n")
    if updated_text != original:
        path.write_text(updated_text, encoding="utf-8", newline="\n")
        updated += 1

print(f"Updated {updated} Java file(s).")
PY
