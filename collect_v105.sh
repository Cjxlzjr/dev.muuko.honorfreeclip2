#!/system/bin/sh
# Read-only diagnostic; does not change module or FreeClip 2 settings.
OUT=/sdcard/Download/hf2_v105_ai_$(date +%Y%m%d_%H%M%S).txt
{
  echo '=== DEVICE ==='
  date
  getprop ro.product.model
  getprop ro.build.version.release
  echo '=== MODULE ACTIVITY ==='
  pm path dev.muuko.honorfreeclip2
  echo '=== HONORFREECLIP2 RECENT LOGS ==='
  logcat -d -v time -t 9000 2>/dev/null | grep -Ei 'HonorFreeClip2|AI key|AI 键|RFCOMM_POOL|FAST_SYNC_TIMING|FAST_WRITE_TIMING' | tail -n 500
} > "$OUT" 2>&1
chmod 644 "$OUT"
echo "$OUT"
