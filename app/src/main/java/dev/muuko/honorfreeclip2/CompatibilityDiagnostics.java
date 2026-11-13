package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;
import java.util.LinkedHashSet;
import java.util.Set;

/** Local logcat only. No device address/name, intent extras, fingerprint or UI output. */
final class CompatibilityDiagnostics {
    private static final Set<String> RECORDED = new LinkedHashSet<>();
    private CompatibilityDiagnostics() { }
    static synchronized void event(String key, String details) {
        String entry = key + " " + details;
        if (RECORDED.contains(entry)) return;
        if (RECORDED.size() >= 64) RECORDED.remove(RECORDED.iterator().next());
        RECORDED.add(entry);
        Log.i(ModuleConstants.TAG, "HF2_COMPAT " + entry);
    }
    static void environment(Activity activity) {
        try {
            PackageInfo host = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            String magic = property("ro.build.version.magic");
            if (magic.isEmpty()) magic = property("ro.build.version.magicui");
            if (magic.isEmpty()) magic = property("ro.build.version.emui");
            event("environment", "module=" + BuildConfig.VERSION_NAME + " sdk=" + Build.VERSION.SDK_INT
                    + " MagicOS=" + clean(magic) + " build=" + clean(Build.DISPLAY)
                    + " audioApk=" + clean(host.versionName) + "(" + host.getLongVersionCode() + ")");
        } catch (Throwable error) {
            event("environment", "unavailable=" + error.getClass().getSimpleName());
        }
    }
    private static String property(String key) {
        try {
            return (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class).invoke(null, key, "");
        } catch (Throwable ignored) { return ""; }
    }
    private static String clean(String value) {
        if (value == null || value.isEmpty()) return "unknown";
        String safe = value.replaceAll("[^A-Za-z0-9._() +/:-]", "_");
        return safe.substring(0, Math.min(96, safe.length()));
    }
}
