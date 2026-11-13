#!/usr/bin/env bash
# Verify the APK against the public certificate of the already published beta.2.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME}"
APK="${1:?Usage: verify_release_apk.sh APK}"
CERT_LOG="$(mktemp)"
trap 'rm -f "$CERT_LOG"' EXIT
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose --print-certs "$APK" > "$CERT_LOG"
EXPECTED="$(tr -d '[:space:]' < signing-certificate.sha256)"
ACTUAL="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$CERT_LOG")"
[[ "$EXPECTED" =~ ^[a-f0-9]{64}$ ]] && [ "$ACTUAL" = "$EXPECTED" ] || {
  echo 'ERROR: APK certificate differs from the published beta.2; do not distribute as an update.' >&2
  exit 1
}
# Reject debuggable artifacts and accidentally mislabeled versions.
BADGING="$("$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging "$APK")"
if grep -q 'application-debuggable' <<< "$BADGING"; then
  echo 'ERROR: debuggable APK cannot be released.' >&2; exit 1
fi
VERSION="$(python3 scripts/release_metadata.py version)"
CODE="$(python3 scripts/release_metadata.py code)"
grep -Fq "name='dev.muuko.honorfreeclip2' versionCode='$CODE' versionName='$VERSION'" <<< "$BADGING"
echo "Verified update certificate SHA-256: $ACTUAL"
echo "Verified package/version: dev.muuko.honorfreeclip2 $VERSION ($CODE)"
