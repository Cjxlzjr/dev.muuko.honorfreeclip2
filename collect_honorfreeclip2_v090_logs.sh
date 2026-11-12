#!/system/bin/sh
# Read-only, filtered diagnostics. Redacts Bluetooth MAC addresses.
OUT="/sdcard/Download/honorfreeclip2_v090_$(date +%Y%m%d_%H%M%S).txt"
{
  echo '=== TIME / DEVICE / APP ==='
  date
  getprop ro.build.display.id
  dumpsys package dev.muuko.honorfreeclip2 2>/dev/null | grep -E 'versionName=|versionCode=' | head -n 3
  echo '=== MODULE LOGS (MAX 500) ==='
  logcat -d -v threadtime 2>/dev/null \
    | grep -Ei 'HonorFreeClip2|Independent EQ|十段 EQ|Full EQ|FreeClip2-rfcomm|AI key probe' \
    | tail -n 500 \
    | sed -E 's/([[:xdigit:]]{2}:){5}[[:xdigit:]]{2}/XX:XX:XX:XX:XX:XX/g'
} > "$OUT"
echo "$OUT"
