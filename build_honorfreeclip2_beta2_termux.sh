#!/data/data/com.termux/files/usr/bin/bash
# HonorFreeClip2 v1.1.0-beta.2, Termux ARM64 builder
# Usage:
#   bash /sdcard/Download/build_honorfreeclip2_beta2_termux.sh
#   bash /sdcard/Download/build_honorfreeclip2_beta2_termux.sh /sdcard/Download/your-source.zip
# Optional: bash ... /sdcard/Download/your-source.zip release
# The release mode requires 4 HF2_KEY* environment variables and a persistent keystore.
set -Eeuo pipefail

msg() { printf '\n[HF2] %s\n' "$*"; }
fail() { printf '\n[HF2 ERROR] %s\n' "$*" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || fail "缺少命令：$1"; }

command -v pkg >/dev/null 2>&1 || fail '只能在 Termux 中执行；不要在 MT 管理器或 adb shell 中执行。'
[ "$(id -u)" -ne 0 ] || fail '请用 Termux 普通用户运行，不要使用 su。'
[ "$(uname -m)" = aarch64 ] || fail '此脚本面向 ARM64 (aarch64) Termux。'

DOWNLOAD=/sdcard/Download
[ -d "$DOWNLOAD" ] && [ -r "$DOWNLOAD" ] && [ -w "$DOWNLOAD" ] || fail '未获得共享存储权限；请先执行 termux-setup-storage 并授权，然后重新运行。'

ZIP="${1:-}"
if [ -z "$ZIP" ]; then
  for candidate in \
    "$DOWNLOAD/HonorFreeClip2-v1.1.0-beta.2-source.zip"; do
    if [ -s "$candidate" ]; then ZIP="$candidate"; break; fi
  done
fi
[ -n "$ZIP" ] && [ -s "$ZIP" ] || fail '源码 ZIP 不存在。请把 V1.1.0-beta.2 源码包放进 /sdcard/Download，或把 ZIP 路径作为第一个参数。'

MODE="${2:-debug}"
case "$MODE" in debug|release) ;; *) fail "构建类型只能是 debug 或 release，收到：$MODE" ;; esac

PROJECT="${HF2_PROJECT_DIR:-$HOME/HonorFreeClip2-beta2}"
mkdir -p "$PROJECT/logs"
LOG="$PROJECT/logs/termux-build-$(date +%Y%m%d_%H%M%S).log"
# Save a complete build log; all project work happens inside Termux private storage.
exec > >(tee -a "$LOG") 2>&1
trap 'rc=$?; if [ "$rc" -ne 0 ]; then printf "\n[HF2 ERROR] 构建失败（退出码 %s），日志：%s\n" "$rc" "$LOG" >&2; fi' EXIT

msg "源码：$ZIP"
msg "构建类型：$MODE"
msg "工作目录：$PROJECT"
msg '检查 Termux 依赖'
NEED_INSTALL=0
for cmd in unzip curl java javac aapt2 python3 sha256sum; do
  command -v "$cmd" >/dev/null 2>&1 || NEED_INSTALL=1
done
if [ "$NEED_INSTALL" -eq 1 ]; then
  msg '安装缺失的构建工具（需要联网）'
  pkg update -y
  pkg install -y openjdk-17 aapt2 python unzip curl coreutils
fi
for cmd in unzip curl java javac aapt2 python3 sha256sum; do require "$cmd"; done

export JAVA_HOME="${PREFIX}/lib/jvm/java-17-openjdk"
[ -x "$JAVA_HOME/bin/java" ] || JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
java -version 2>&1 | head -n 3
JAVA_SPEC="$($JAVA_HOME/bin/java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)"
[ "$JAVA_SPEC" = '17' ] || fail "需要 JDK 17，当前检测到：${JAVA_SPEC:-未知}；请检查 Termux Java 环境。"
AAPT2="$(command -v aapt2)"
"$AAPT2" version || fail 'Termux ARM64 aapt2 无法正常运行。'

# Honor an existing Android SDK if already configured, otherwise use the previous HF2 location.
if [ -n "${ANDROID_HOME:-}" ]; then
  SDK="$ANDROID_HOME"
elif [ -s "$HOME/android-sdk/platforms/android-35/android.jar" ]; then
  SDK="$HOME/android-sdk"
elif [ -s "$PREFIX/opt/android-sdk/platforms/android-35/android.jar" ]; then
  SDK="$PREFIX/opt/android-sdk"
else
  SDK="$HOME/android-sdk"
fi
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
msg "Android SDK：$SDK"

if [ ! -s "$SDK/platforms/android-35/android.jar" ] || [ ! -d "$SDK/build-tools/35.0.0" ]; then
  SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
  if [ ! -f "$SDKMANAGER" ]; then
    msg '首次准备 Android SDK 命令行工具（需要联网及一定存储空间）'
    CACHE="$HOME/.cache/hf2-termux"
    mkdir -p "$CACHE" "$SDK/cmdline-tools"
    CT_ZIP="$CACHE/commandlinetools-linux-11076708_latest.zip"
    [ -s "$CT_ZIP" ] || curl -fL --retry 3 --connect-timeout 30 \
      'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip' -o "$CT_ZIP"
    unzip -tq "$CT_ZIP" >/dev/null || fail 'Android SDK 命令行工具下载损坏。'
    CT_TMP="$CACHE/commandlinetools-unpacked"
    rm -rf "$CT_TMP"
    mkdir -p "$CT_TMP"
    unzip -q "$CT_ZIP" -d "$CT_TMP"
    [ -f "$CT_TMP/cmdline-tools/bin/sdkmanager" ] || fail 'SDK 命令行工具解压结构错误。'
    mv "$CT_TMP/cmdline-tools" "$SDK/cmdline-tools/latest"
  fi
  msg '安装 Android SDK Platform 35 / Build Tools 35.0.0'
  # Accept SDK licenses. 'yes' may normally exit with SIGPIPE after sdkmanager ends.
  set +e
  yes | bash "$SDKMANAGER" --sdk_root="$SDK" --licenses
  LICENSE_RC=${PIPESTATUS[1]}
  set -e
  [ "$LICENSE_RC" -eq 0 ] || fail 'SDK 许可确认失败；请检查上面的详细错误。'
  bash "$SDKMANAGER" --sdk_root="$SDK" \
    'platforms;android-35' 'build-tools;35.0.0'
fi
[ -s "$SDK/platforms/android-35/android.jar" ] || fail 'Android SDK Platform 35 缺失。'
[ -d "$SDK/build-tools/35.0.0" ] || fail 'Android Build Tools 35.0.0 缺失。'

GRADLE_VERSION=8.11.1
GRADLE="${HF2_GRADLE_BIN:-$HOME/.local/opt/gradle-$GRADLE_VERSION/bin/gradle}"
if [ ! -x "$GRADLE" ]; then
  msg '首次安装 Gradle 8.11.1（需要联网）'
  CACHE="$HOME/.cache/hf2-termux"
  DIST="$HOME/.local/opt"
  mkdir -p "$CACHE" "$DIST"
  GRADLE_ZIP="$CACHE/gradle-$GRADLE_VERSION-bin.zip"
  [ -s "$GRADLE_ZIP" ] || curl -fL --retry 3 --connect-timeout 30 \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$GRADLE_ZIP"
  unzip -tq "$GRADLE_ZIP" >/dev/null || fail 'Gradle 压缩包损坏。'
  unzip -oq "$GRADLE_ZIP" -d "$DIST"
fi
[ -x "$GRADLE" ] || fail "Gradle 不存在：$GRADLE"
"$GRADLE" --version | sed -n '1,18p'

msg '校验并解压源码'
unzip -tq "$ZIP" >/dev/null || fail '源码 ZIP 损坏。'
# ZIP contents are rootless. Re-extraction updates source files but preserves Gradle caches.
unzip -oq "$ZIP" -d "$PROJECT"
[ -s "$PROJECT/app/build.gradle" ] || fail '源码 ZIP 内缺少 app/build.gradle，检查是否使用了正确的包。'
# Older beta2 ZIP accidentally retained a now-obsolete private icon mapping document.
# It describes the removed vendor images and is explicitly forbidden by check_release.py.
if [ -f "$PROJECT/PRIVATE_ICON_MAPPING.md" ]; then
  msg '移除旧版遗留文件 PRIVATE_ICON_MAPPING.md'
  rm -f "$PROJECT/PRIVATE_ICON_MAPPING.md"
fi
printf 'sdk.dir=%s\n' "$SDK" > "$PROJECT/local.properties"

cd "$PROJECT"
msg '运行静态发布检查和 JVM 协议回归'
python3 tests/check_release.py
if [ "${HF2_SKIP_HOST_TESTS:-0}" != '1' ]; then
  bash tests/run_host_tests.sh
else
  msg '已按 HF2_SKIP_HOST_TESTS=1 跳过 JVM 回归测试'
fi

if [ "$MODE" = release ]; then
  for k in HF2_KEYSTORE_PATH HF2_KEYSTORE_PASSWORD HF2_KEY_ALIAS HF2_KEY_PASSWORD; do
    [ -n "${!k:-}" ] || fail "Release 缺少签名环境变量 $k；不要将私钥或密码写进脚本。"
  done
  [ -r "$HF2_KEYSTORE_PATH" ] || fail "签名文件不存在或不可读：$HF2_KEYSTORE_PATH"
  TARGET=':app:assembleRelease'
  APK='app/build/outputs/apk/release/app-release.apk'
  OUT="$DOWNLOAD/HonorFreeClip2-v1.1.0-beta.2-release.apk"
else
  TARGET=':app:assembleDebug'
  APK='app/build/outputs/apk/debug/app-debug.apk'
  OUT="$DOWNLOAD/HonorFreeClip2-v1.1.0-beta.2-debug.apk"
fi

msg "执行 Gradle $TARGET"
"$GRADLE" --no-daemon --max-workers=2 --stacktrace \
  -Pandroid.aapt2FromMavenOverride="$AAPT2" \
  -Dorg.gradle.native=false \
  "$TARGET"

[ -s "$APK" ] || fail "构建结束但未找到 APK：$APK"
unzip -tq "$APK" >/dev/null || fail '生成的 APK ZIP 结构损坏。'

if [ "$MODE" = release ]; then
  APKSIGNER="$SDK/build-tools/35.0.0/apksigner"
  [ -f "$APKSIGNER" ] || fail '缺少 SDK apksigner，不能验证 Release 签名。'
  bash "$APKSIGNER" verify --verbose --print-certs "$APK" || fail 'Release APK 签名校验失败。'
fi

cp -f "$APK" "$OUT"
msg '构建成功'
ls -lh "$OUT"
sha256sum "$OUT"
printf 'APK 路径：%s\n完整日志：%s\n' "$OUT" "$LOG"
if [ "$MODE" = debug ]; then
  printf '%s\n' '提示：Debug APK 使用测试签名，不作为公开发布的正式签名版。'
fi
