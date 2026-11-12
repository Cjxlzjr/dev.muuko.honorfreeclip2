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
"$GRADLE" :app:assembleRelease --no-daemon --stacktrace
APK=app/build/outputs/apk/release/app-release.apk
[ -s "$APK" ] || { echo 'ERROR: signed release APK missing' >&2; exit 1; }
: "${ANDROID_HOME:?ERROR: Set ANDROID_HOME to Android SDK 35}"
APKSIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
[ -x "$APKSIGNER" ] || { echo "ERROR: no apksigner: $APKSIGNER" >&2; exit 1; }
mkdir -p dist
cp "$APK" dist/HonorFreeClip2-v1.1.0-beta.2-release.apk
"$APKSIGNER" verify --verbose --print-certs dist/HonorFreeClip2-v1.1.0-beta.2-release.apk
(cd dist && sha256sum HonorFreeClip2-v1.1.0-beta.2-release.apk > SHA256SUMS.txt)
echo 'Created dist/HonorFreeClip2-v1.1.0-beta.2-release.apk and dist/SHA256SUMS.txt'
