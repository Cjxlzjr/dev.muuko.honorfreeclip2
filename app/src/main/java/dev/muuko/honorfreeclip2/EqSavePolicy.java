package dev.muuko.honorfreeclip2;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Pure-Java safety gate shared by the editor, transport, undo and host-side tests. */
final class EqSavePolicy {
    private EqSavePolicy() { }

    static final int MAX_NAME_BYTES = 24; // Readback record stores a padded 24-byte UTF-8 name.

    static int targetId(EqualizerState state) {
        if (state == null || !state.supported || !state.hasTenBandCurve()) return -1;
        // V0.9 deliberately allows only the selected custom slot. New slot creation is not validated.
        return state.isCustom() ? state.selectedId : -1;
    }

    static boolean validName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        int bytes = name.trim().getBytes(StandardCharsets.UTF_8).length;
        return bytes >= 1 && bytes <= MAX_NAME_BYTES;
    }

    static boolean validGains(int[] values) {
        if (values == null || values.length != EqualizerState.BANDS) return false;
        for (int v : values) if (v < EqualizerState.MIN_GAIN || v > EqualizerState.MAX_GAIN) return false;
        return true;
    }

    static boolean overwrites(EqualizerState state, int id) {
        if (state == null) return false;
        for (EqualizerState.Preset p : state.customPresets) if (p.id == id) return true;
        return false;
    }

    static int changedBands(EqualizerState state, int[] requested) {
        if (state == null || !state.hasTenBandCurve() || !validGains(requested)) return -1;
        int changed = 0;
        for (int i = 0; i < requested.length; i++) {
            if (requested[i] != state.selectedGains.get(i)) changed++;
        }
        return changed;
    }

    /** Full editor after proven single-band direct write. Still no unverified slot creation or switch. */
    static boolean fullAllowed(EqualizerState current, int presetId, String name, int[] gains) {
        if (current == null || !current.supported || !current.isCustom()
                || !current.hasTenBandCurve() || current.selectedId != presetId
                || !validName(name) || !validGains(gains)) return false;
        // Do not silently collide with another saved slot when renaming.
        String normalized = name.trim();
        for (EqualizerState.Preset preset : current.customPresets) {
            if (preset.id != presetId && normalized.equalsIgnoreCase(preset.name.trim())) return false;
        }
        return changedBands(current, gains) > 0
                || !normalized.equals(current.selectedName == null ? "" : current.selectedName.trim());
    }

    /** One-step rollback only to the same selected ID and a previously verified snapshot. */
    static boolean canRestore(EqualizerState current, EqualizerState backup) {
        if (current == null || backup == null || current.selectedId != backup.selectedId
                || !backup.isCustom() || !backup.hasTenBandCurve()) return false;
        return fullAllowed(current, backup.selectedId, backup.selectedName,
                toArray(backup.selectedGains));
    }

    static int[] toArray(List<Integer> values) {
        if (values == null || values.size() != EqualizerState.BANDS) return null;
        int[] copy = new int[values.size()];
        for (int i = 0; i < copy.length; i++) copy[i] = values.get(i);
        return copy;
    }

    /** Prior experimental policy retained only for historical tests, not used by V0.9 writes. */
    static boolean trialAllowed(EqualizerState current, int presetId, String name, int[] gains) {
        if (!fullAllowed(current, presetId, name, gains)
                || !name.trim().equals(current.selectedName)) return false;
        int changed = 0;
        for (int i = 0; i < EqualizerState.BANDS; i++) {
            int delta = Math.abs(gains[i] - current.selectedGains.get(i));
            if (delta > 10) return false;
            if (delta != 0) changed++;
        }
        return changed == 1;
    }

    static EqualizerState afterConfirmed(EqualizerState original, int id, String name, int[] gains) {
        if (original == null || !validName(name) || !validGains(gains)
                || id < 100 || id > 102) return original;
        List<EqualizerState.Preset> result = new ArrayList<>();
        for (EqualizerState.Preset p : original.customPresets) if (p.id != id) result.add(p);
        List<Integer> g = new ArrayList<>();
        for (int v : gains) g.add(v);
        result.add(new EqualizerState.Preset(id, name.trim(), g));
        return new EqualizerState(true, id, 10, original.builtInIds, name.trim(), g, result);
    }
}
