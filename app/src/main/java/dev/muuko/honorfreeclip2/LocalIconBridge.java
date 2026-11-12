package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;

/**
 * Bundled resources, deliberately obtained from this module's installed APK.
 * NEVER accesses com.huawei.smartaudio or loads its APK at runtime.
 * All bundled hf2_*.png assets are rasterized from original, editable project SVGs
 * under assets/icons/. No extracted vendor icons are redistributed.
 */
final class LocalIconBridge {
    private static final String PACKAGE = "dev.muuko.honorfreeclip2";
    private LocalIconBridge() { }

    static Drawable icon(Activity a, int kind) {
        switch (kind) {
            case SmartAudioIcon.SOUND: return load(a, "hf2_ic_eq");
            case SmartAudioIcon.GESTURE: return load(a, "hf2_gesture");
            case SmartAudioIcon.HEAD: return load(a, "hf2_head");
            case SmartAudioIcon.VOICE: return load(a, "hf2_voice");
            case SmartAudioIcon.WEAR: return load(a, "hf2_ic_wear_detection");
            case SmartAudioIcon.CASE: return load(a, "hf2_ic_charging_case_setting");
            case SmartAudioIcon.DEVICE: return load(a, "hf2_ic_settings");
            case SmartAudioIcon.LAB: return load(a, "hf2_ic_laboratory");
            case SmartAudioIcon.UPDATE: return load(a, "hf2_ic_update");
            default: return null;
        }
    }

    static Drawable named(Activity a, String originalName) {
        if (!originalName.startsWith("icon_spatial_audio_")) return null;
        String local = "hf2_spatial_" + originalName.substring("icon_spatial_audio_".length());
        if (!local.matches("hf2_spatial_(off|fixed|follow)_(normal|selected)")) return null;
        return load(a, local);
    }

    private static Drawable load(Activity activity, String name) {
        try {
            Context moduleContext = activity.createPackageContext(PACKAGE, 0);
            Resources res = moduleContext.getResources();
            int id = res.getIdentifier(name, "drawable", PACKAGE);
            if (id != 0) return res.getDrawable(id, moduleContext.getTheme()).mutate();
        } catch (Throwable t) {
            Log.w(ModuleConstants.TAG, "Bundled icon unavailable: " + name, t);
        }
        return null; // Safe self-drawn vector fallback.
    }
}
