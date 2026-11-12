#!/system/bin/sh
# Read-only diagnostics: does not change FreeClip 2 settings.
OUT=/sdcard/Download/hf2_v11_$(date +%Y%m%d_%H%M%S).txt
{
  date
  getprop ro.product.model
  getprop ro.build.version.release
  pm path dev.muuko.honorfreeclip2
  logcat -d -v time -t 9000 2>/dev/null | grep -Ei 'HonorFreeClip2|CUSTOM_PRESET_ACTION|FAST_SYNC_TIMING|FAST_WRITE_TIMING|RFCOMM_POOL|EQ|AI.?key' | tail -n 550
} > "$OUT" 2>&1
chmod 644 "$OUT"
echo "$OUT"
