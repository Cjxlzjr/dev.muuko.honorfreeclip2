package dev.muuko.honorfreeclip2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, CRC-verified state read from Huawei AAM EQ response. Gain unit: 0.1 dB. */
final class EqualizerState {
    static final int BANDS = 10;
    static final int MIN_GAIN = -60;
    static final int MAX_GAIN = 60;
    static final int[] FREQUENCIES = {60,125,250,500,1000,2000,4000,8000,12000,16000};

    static final class Preset {
        final int id;
        final String name;
        final List<Integer> gains;
        Preset(int id, String name, List<Integer> gains) {
            this.id = id;
            this.name = name;
            this.gains = Collections.unmodifiableList(new ArrayList<>(gains));
        }
    }

    final boolean supported;
    final int selectedId;
    final int bandCount;
    final List<Integer> builtInIds;
    final String selectedName;
    final List<Integer> selectedGains;
    final List<Preset> customPresets;
    /** TLV08 actually appeared in the CRC-verified report. Empty + absent is NOT an empty catalog. */
    final boolean customCatalogKnown;

    EqualizerState(boolean supported, int selectedId, int bandCount,
                   List<Integer> builtInIds, String selectedName, List<Integer> selectedGains,
                   List<Preset> customPresets) {
        this(supported, selectedId, bandCount, builtInIds, selectedName,
                selectedGains, customPresets, true);
    }

    EqualizerState(boolean supported, int selectedId, int bandCount,
                   List<Integer> builtInIds, String selectedName, List<Integer> selectedGains,
                   List<Preset> customPresets, boolean customCatalogKnown) {
        this.customCatalogKnown = customCatalogKnown;
        this.supported = supported;
        this.selectedId = selectedId;
        this.bandCount = bandCount;
        this.builtInIds = Collections.unmodifiableList(new ArrayList<>(builtInIds));
        this.selectedName = selectedName;
        this.selectedGains = selectedGains == null ? null :
                Collections.unmodifiableList(new ArrayList<>(selectedGains));
        this.customPresets = Collections.unmodifiableList(new ArrayList<>(customPresets));
    }

    boolean isCustom() { return selectedId >= 0x64 && selectedId <= 0x66; }
    boolean hasTenBandCurve() { return bandCount == BANDS && selectedGains != null
            && selectedGains.size() == BANDS; }
    String displayName() {
        if (isCustom()) {
            return selectedName == null || selectedName.isEmpty() ? "自定义音效" : selectedName;
        }
        switch (selectedId) {
            case 1: return "默认";
            case 10: return "运动增强";
            case 3: return "高音增强";
            case 9: return "清晰人声";
            default: return "音效 ID " + selectedId;
        }
    }
}
