#!/usr/bin/env bash
# Package only Git-tracked source; credentials/build caches cannot enter the ZIP.
set -Eeuo pipefail
cd "$(dirname "$0")/.."
MODE="${1:-verification}"
VERSION="$(python3 scripts/release_metadata.py version)"
mkdir -p dist
case "$MODE" in
  verification)
    APK=app/build/outputs/apk/verification/app-verification-unsigned.apk
    OUT="HonorFreeClip2-v$VERSION-unsigned.apk"
    ;;
  release)
    APK=app/build/outputs/apk/release/app-release.apk
    bash scripts/verify_release_apk.sh "$APK"
    OUT="HonorFreeClip2-v$VERSION-release.apk"
    ;;
  *) echo 'Usage: prepare_dist.sh verification|release' >&2; exit 1 ;;
esac
test -s "$APK"
test -z "$(git status --porcelain --untracked-files=normal)" || {
  echo 'Commit the reviewed source before packaging a release archive.' >&2; exit 1
}
cp "$APK" "dist/$OUT"
git archive --format=zip --output="dist/HonorFreeClip2-v$VERSION-source.zip" HEAD
cp LICENSE NOTICE.md ASSETS_NOTICE.md docs/RELEASE_NOTES.md dist/
(cd dist && sha256sum "$OUT" "HonorFreeClip2-v$VERSION-source.zip" LICENSE NOTICE.md ASSETS_NOTICE.md RELEASE_NOTES.md > SHA256SUMS.txt)
echo "Prepared dist/$OUT and matching committed source."
