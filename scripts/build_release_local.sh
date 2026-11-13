#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
for k in HF2_KEYSTORE_PATH HF2_KEYSTORE_PASSWORD HF2_KEY_ALIAS HF2_KEY_PASSWORD; do
  test -n "${!k:-}" || { echo "ERROR: missing signing environment $k" >&2; exit 1; }
done
[ -r "$HF2_KEYSTORE_PATH" ] || { echo "ERROR: unreadable JKS file" >&2; exit 1; }
if command -v gradle >/dev/null 2>&1; then
  GRADLE=gradle
elif [ -x "$HOME/.local/opt/gradle-8.11.1/bin/gradle" ]; then
  GRADLE="$HOME/.local/opt/gradle-8.11.1/bin/gradle"
else
  echo "ERROR: Gradle 8.11.1 not found" >&2; exit 1
fi
python3 tests/check_release.py
bash tests/run_host_tests.sh
"$GRADLE" :app:testVerificationUnitTest :app:lintVerification :app:assembleRelease --no-daemon --stacktrace
bash scripts/prepare_dist.sh release
