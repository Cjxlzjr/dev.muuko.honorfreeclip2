#!/system/bin/sh
# Compatibility diagnostics only; no device names, addresses or intent dumps.
umask 077
OUT="/sdcard/Download/hf2_compat_$(date +%Y%m%d_%H%M%S).txt"
{
  date
  logcat -d -v time -t 10000 2>/dev/null | grep 'HF2_COMPAT ' | tail -n 150
} > "$OUT"
echo "$OUT"
