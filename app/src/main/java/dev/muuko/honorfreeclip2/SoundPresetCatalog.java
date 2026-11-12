package dev.muuko.honorfreeclip2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 2B/4A detailed equalizer state is the source of truth for the active sound preset.
 * Some firmware reports an active custom ID (0x64..0x66), which is NOT one of the
 * four built-in selectable presets. The UI must not interpret it as "not read".
 */
final class SoundPresetCatalog {
    static final class Entry {
        final int id;
        final String label;
        Entry(int id, String label) { this.id = id; this.label = label; }
    }
    private SoundPresetCatalog() { }

    static boolean supportedBuiltIn(int id) {
        return id == 1 || id == 10 || id == 3 || id == 9;
    }
    static boolean isCustom(int id) { return id >= 0x64 && id <= 0x66; }

    static String builtInName(int id) {
        switch (id) {
            case 1: return "默认";
            case 10: return "运动增强";
            case 3: return "高音增强";
            case 9: return "清晰人声";
            default: return "未知内置音效 ID " + id;
        }
    }

    static String currentLabel(Integer current, EqualizerState eq) {
        if (eq != null) {
            int id = eq.selectedId;
            if (isCustom(id)) {
                String name = eq.selectedName;
                return (name == null || name.trim().isEmpty() ? "自定义音效" : name) + "（自定义）";
            }
            return supportedBuiltIn(id) ? builtInName(id) : "音效 ID " + id + "（未映射）";
        }
        if (current == null) return "未读取";
        if (isCustom(current)) return "自定义音效（未读取详情）";
        return supportedBuiltIn(current) ? builtInName(current) : "音效 ID " + current + "（未映射）";
    }

    /**
     * Prefer firmware-advertised, recognized built-in IDs. If the optional list
     * is omitted, fall back to the four FreeClip 2 presets verified in HuaweiPods.
     * The actual write is still checked by a fresh device query and readback.
     */
    static List<Entry> writableOptions(EqualizerState eq) {
        if (eq == null || !eq.supported)
            return Collections.emptyList();
        List<Entry> result = new ArrayList<>();
        // Stable display ordering, independent of device response ordering.
        for (int id : new int[]{1,10,3,9}) {
            if (eq.builtInIds.isEmpty() || eq.builtInIds.contains(id))
                result.add(new Entry(id,builtInName(id)));
        }
        return Collections.unmodifiableList(result);
    }

    static boolean canWrite(EqualizerState eq, int desired) {
        if (eq == null || !eq.supported || !supportedBuiltIn(desired)) return false;
        for (Entry e : writableOptions(eq)) if (e.id == desired) return true;
        return false;
    }

    static boolean samePreflight(EqualizerState snapshot, EqualizerState current) {
        if (snapshot == null || current == null || !snapshot.supported || !current.supported
                || snapshot.selectedId != current.selectedId) return false;
        // Custom preset contents must not have been modified by another application.
        if (isCustom(snapshot.selectedId)) {
            return java.util.Objects.equals(snapshot.selectedName,current.selectedName)
                    && java.util.Objects.equals(snapshot.selectedGains,current.selectedGains);
        }
        return true;
    }
}
