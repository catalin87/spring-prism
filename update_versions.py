import os
import re

def update_version(file_path, old_v, new_v):
    if not os.path.isfile(file_path):
        return
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    if old_v in content:
        new_content = content.replace(old_v, new_v)
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Updated {file_path}")

# Get all files containing 1.1.0-SNAPSHOT
files = [line.strip() for line in os.popen('git grep -l "1.1.0-SNAPSHOT"').readlines()]

for f in files:
    update_version(f, "1.1.0-SNAPSHOT", "1.1.0")
