#!/usr/bin/env bash
# Rebuilds app/src/main/assets/fadalias_sepolicy.zip from sepolicy-module/ (the app offers it as "Install module").
# Uses the JDK's `jar` tool, so no extra packages are needed.
set -euo pipefail
cd "$(dirname "$0")/.."
out="app/src/main/assets/fadalias_sepolicy.zip"
mkdir -p "$(dirname "$out")"
rm -f "$out"
if command -v jar >/dev/null 2>&1; then
  jar --create --no-manifest --file "$out" -C sepolicy-module .
elif command -v zip >/dev/null 2>&1; then
  (cd sepolicy-module && zip -qr -X "../$out" .)
else
  echo "need jar (JDK) or zip to pack the SELinux module" >&2; exit 1
fi
echo "packed $out"
