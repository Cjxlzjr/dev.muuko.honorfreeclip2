#!/usr/bin/env python3
"""Read the single Android version source without executing the Gradle build."""
import re
import sys
from pathlib import Path
root = Path(__file__).resolve().parent.parent
source = (root / "app/build.gradle").read_text(encoding="utf-8")
version = re.search(r"versionName\s+'([0-9]+\.[0-9]+\.[0-9]+(?:-[a-z]+\.[0-9]+)?)'", source).group(1)
code = int(re.search(r"versionCode\s+(\d+)", source).group(1))
values = {"version": version, "code": str(code), "tag": "v" + version}
if len(sys.argv) != 2 or sys.argv[1] not in values:
    raise SystemExit("Usage: release_metadata.py version|code|tag")
print(values[sys.argv[1]])
