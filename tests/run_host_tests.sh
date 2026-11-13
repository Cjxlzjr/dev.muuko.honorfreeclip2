#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/app/src/main/java/dev/muuko/honorfreeclip2"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
# Native Windows Java needs a Windows classpath, while bash uses POSIX paths.
PYTHON=python3
JAVA_TMP="$TMP"
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) PYTHON=python; JAVA_TMP="$(cygpath -w "$TMP")" ;; esac
javac -encoding UTF-8 -d "$JAVA_TMP"   "$SRC/FreeClipSetting.java" "$SRC/FreeClipProtocol.java"   "$SRC/EqualizerState.java" "$SRC/EqualizerCodec.java" "$SRC/EqSavePolicy.java" \
  "$SRC/DirectEqualizerCodec.java" "$SRC/CustomPresetPolicy.java" "$SRC/SoundPresetCatalog.java" "$SRC/AiKeyActionCodec.java" "$SRC/AiKeyProbeCodec.java" "$SRC/ReadIdlePolicy.java" "$SRC/ReadBatchPolicy.java" "$SRC/SocketReusePolicy.java"   "$ROOT/tests/CustomPresetPolicySelfTest.java" "$ROOT/tests/ProtocolSelfTest.java" "$ROOT/tests/EqualizerCodecSelfTest.java" "$ROOT/tests/EqSavePolicySelfTest.java" \
  "$ROOT/tests/IndependentEqSelfTest.java" "$ROOT/tests/AiKeyProbeSelfTest.java" "$ROOT/tests/FullEqSelfTest.java" "$ROOT/tests/SoundPresetSelfTest.java" "$ROOT/tests/AiKeyStateSelfTest.java" "$ROOT/tests/AiKeyRealFrameSelfTest.java" "$ROOT/tests/AiKeyIndependentWriteSelfTest.java" "$ROOT/tests/FastReadSelfTest.java" "$ROOT/tests/QuickTransportSelfTest.java"
java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.ProtocolSelfTest
java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.EqualizerCodecSelfTest

java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.EqSavePolicySelfTest

java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.IndependentEqSelfTest
java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.AiKeyProbeSelfTest

java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.FullEqSelfTest

java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.SoundPresetSelfTest
java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.AiKeyStateSelfTest
java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.AiKeyRealFrameSelfTest
java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.AiKeyIndependentWriteSelfTest

java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.FastReadSelfTest

java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.QuickTransportSelfTest

# compile modified Android-facing coordinator + RFCOMM transport against test-only stubs
javac -encoding UTF-8 -d "$JAVA_TMP" \
  $(find "$ROOT/tests/android_stubs" -name '*.java') \
  "$SRC/LifecycleHookResolver.java" "$SRC/TransportTaskRunner.java" "$SRC/ModuleConstants.java" "$SRC/FreeClipTransport.java" "$SRC/ControlCoordinator.java" \
  "$ROOT/tests/LifecycleHookResolverSelfTest.java" "$ROOT/tests/ProtocolRobustnessSelfTest.java" "$ROOT/tests/TransportTaskRunnerSelfTest.java" "$ROOT/tests/TransportRecoverySmokeTest.java" "$ROOT/tests/TransportPoolSmokeTest.java" "$ROOT/tests/AiKeyTransportSmokeTest.java" "$ROOT/tests/MultiPresetTransportSmokeTest.java" \
  "$SRC/FreeClipSetting.java" "$SRC/FreeClipProtocol.java" "$SRC/EqualizerState.java" "$SRC/EqualizerCodec.java" \
  "$SRC/EqSavePolicy.java" "$SRC/DirectEqualizerCodec.java" "$SRC/CustomPresetPolicy.java" "$SRC/SoundPresetCatalog.java" \
  "$SRC/AiKeyActionCodec.java" "$SRC/AiKeyProbeCodec.java" "$SRC/ReadIdlePolicy.java" "$SRC/ReadBatchPolicy.java" "$SRC/SocketReusePolicy.java"
java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.TransportPoolSmokeTest
java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.AiKeyTransportSmokeTest

java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.CustomPresetPolicySelfTest

java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.MultiPresetTransportSmokeTest

"$PYTHON" "$ROOT/tests/check_release.py"

"$PYTHON" "$ROOT/tests/check_bottom_sheets.py"

java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.LifecycleHookResolverSelfTest
java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.ProtocolRobustnessSelfTest
java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.TransportTaskRunnerSelfTest
java -cp "$JAVA_TMP" dev.muuko.honorfreeclip2.TransportRecoverySmokeTest
