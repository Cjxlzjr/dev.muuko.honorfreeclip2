package dev.muuko.honorfreeclip2;

import java.util.Map;

/** Classifies CRC-verified reports from one combined FreeClip 2 settings query. */
final class ReadBatchPolicy {
    private ReadBatchPolicy() { }

    static boolean hasBoolean(Map<FreeClipSetting,Integer> states) {
        return containsAny(states, FreeClipSetting.WEAR, FreeClipSetting.DROP,
                FreeClipSetting.ADAPTIVE, FreeClipSetting.HEAD_MOTION,
                FreeClipSetting.NOISY, FreeClipSetting.LOW_LATENCY,
                FreeClipSetting.DUAL_DEVICE, FreeClipSetting.CASE_SOUND);
    }

    static boolean hasAudio(Map<FreeClipSetting,Integer> states, EqualizerState eq) {
        return eq != null || containsAny(states, FreeClipSetting.SPATIAL_MODE,
                FreeClipSetting.SPATIAL_SCENE, FreeClipSetting.SOUND_EFFECT);
    }

    static boolean hasGesture(Map<FreeClipSetting,Integer> states) {
        return containsAny(states, FreeClipSetting.DOUBLE_LEFT, FreeClipSetting.DOUBLE_RIGHT,
                FreeClipSetting.TRIPLE_LEFT, FreeClipSetting.TRIPLE_RIGHT,
                FreeClipSetting.SWIPE_LEFT, FreeClipSetting.SWIPE_RIGHT);
    }

    /** Require multi-frame coverage for the shortest quiet period; a partial batch waits longer. */
    static boolean allGroupsResponded(Map<FreeClipSetting,Integer> states, EqualizerState eq) {
        return booleanCount(states) >= 3 && gestureCount(states) >= 2
                && states.containsKey(FreeClipSetting.SPATIAL_MODE) && eq != null;
    }

    static int booleanCount(Map<FreeClipSetting,Integer> states) {
        return count(states, FreeClipSetting.WEAR, FreeClipSetting.DROP,
                FreeClipSetting.ADAPTIVE, FreeClipSetting.HEAD_MOTION,
                FreeClipSetting.NOISY, FreeClipSetting.LOW_LATENCY,
                FreeClipSetting.DUAL_DEVICE, FreeClipSetting.CASE_SOUND);
    }

    static int gestureCount(Map<FreeClipSetting,Integer> states) {
        return count(states, FreeClipSetting.DOUBLE_LEFT, FreeClipSetting.DOUBLE_RIGHT,
                FreeClipSetting.TRIPLE_LEFT, FreeClipSetting.TRIPLE_RIGHT,
                FreeClipSetting.SWIPE_LEFT, FreeClipSetting.SWIPE_RIGHT);
    }

    private static int count(Map<FreeClipSetting,Integer> states, FreeClipSetting... keys) {
        int count = 0;
        for (FreeClipSetting key : keys) if (states.containsKey(key)) count++;
        return count;
    }

    private static boolean containsAny(Map<FreeClipSetting,Integer> states, FreeClipSetting... keys) {
        for (FreeClipSetting key : keys) if (states.containsKey(key)) return true;
        return false;
    }
}
