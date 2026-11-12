#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
fail() { echo "ERROR: $*" >&2; exit 1; }
command -v pkg >/dev/null || fail '请在 Termux 普通用户环境运行'
[ "$(id -u)" -ne 0 ] || fail '不要以 Root 身份编译'
ZIP="${1:-/sdcard/Download/HonorFreeClip2-v1.1.0-beta.2-source.zip}"
[ -s "$ZIP" ] || fail "找不到源码 ZIP：$ZIP"
for c in unzip java aapt2; do command -v "$c" >/dev/null || fail "缺少 $c"; done
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
[ -s "$SDK/platforms/android-35/android.jar" ] || fail "缺少 Android SDK 35：$SDK"
GRADLE="$HOME/.local/opt/gradle-8.11.1/bin/gradle"
[ -x "$GRADLE" ] || fail "缺少 Gradle 8.11.1：$GRADLE"
export JAVA_HOME="${PREFIX}/lib/jvm/java-17-openjdk"
[ -x "$JAVA_HOME/bin/java" ] || JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
export PATH="$JAVA_HOME/bin:$PATH" ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
PROJECT="$HOME/HonorFreeClip2-beta2"
mkdir -p "$PROJECT"
unzip -oq "$ZIP" -d "$PROJECT"
[ -s "$PROJECT/app/build.gradle" ] || fail '源码压缩包缺少 app/build.gradle'
printf 'sdk.dir=%s\n' "$SDK" > "$PROJECT/local.properties"
cd "$PROJECT"
python3 tests/check_release.py || fail '静态检查失败（需先安装 Python）'
"$GRADLE" --no-daemon --stacktrace \
  -Pandroid.aapt2FromMavenOverride="$(command -v aapt2)" \
  -Dorg.gradle.native=false :app:assembleDebug
APK=app/build/outputs/apk/debug/app-debug.apk
[ -s "$APK" ] || fail '未生成 Debug APK'
unzip -tq "$APK" >/dev/null || fail 'APK ZIP 完整性检查失败'
OUT=/sdcard/Download/HonorFreeClip2-v1.1.0-beta.2-debug.apk
cp "$APK" "$OUT"
sha256sum "$OUT"
echo "Debug APK: $OUT（仅供测试，不是正式签名发布版）"
