package dev.muuko.honorfreeclip2;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Port of HuaweiPods 0x2B/0x4A CRC-verified EQ read decoder. */
final class EqualizerCodec {
    static final byte[] QUERY = FreeClipProtocol.hex("5A0005002B4A02008C46");
    private static final int MAX_SIZE = 64 * 1024;
    private static final int CUSTOM_RECORD_SIZE = 36;
    private EqualizerCodec() { }

    static EqualizerState parse(byte[] stream) {
        if (stream == null || stream.length == 0 || stream.length > MAX_SIZE) return null;
        EqualizerState latest = null;
        int offset = 0;
        while (offset + 9 <= stream.length) {
            if (u(stream[offset]) != 0x5A || u(stream[offset + 1]) != 0) { offset++; continue; }
            int size = 5 + u(stream[offset + 2]) + (u(stream[offset + 3]) << 8);
            if (size < 9 || size > 4096 || offset + size > stream.length) { offset++; continue; }
            byte[] frame = Arrays.copyOfRange(stream, offset, offset + size);
            int crc = FreeClipProtocol.crc16(frame, size - 2);
            if (u(frame[size - 2]) != (crc >>> 8) || u(frame[size - 1]) != (crc & 255)) {
                offset++;
                continue;
            }
            offset += size;
            if (u(frame[4]) != 0x2B || u(frame[5]) != 0x4A) continue;
            Map<Integer,byte[]> fields = uniqueFields(frame);
            if (fields == null) continue;
            Integer selectedId = one(fields.get(2));
            if (selectedId == null) continue;
            Integer supportedField = one(fields.get(1));
            boolean supported = supportedField != null && supportedField == 1;
            Integer countField = one(fields.get(5));
            byte[] rawCurve = fields.get(6);
            int bandCount = countField != null && countField >= 1 && countField <= 10 ? countField
                    : rawCurve != null && rawCurve.length >= 1 && rawCurve.length <= 10 ? rawCurve.length : 10;
            List<Integer> selected = rawCurve != null && rawCurve.length == bandCount
                    ? parseGains(rawCurve) : null;
            List<EqualizerState.Preset> presets = parsePresets(fields.get(8));
            if (presets == null) continue;
            String name = safeName(fields.get(7));
            for (EqualizerState.Preset preset : presets) {
                if (preset.id == selectedId) {
                    selected = preset.gains;
                    name = preset.name;
                    break;
                }
            }
            List<Integer> builtins = new ArrayList<>();
            if (fields.get(3) != null)
                for (byte b : fields.get(3)) builtins.add(u(b));
            latest = new EqualizerState(supported, selectedId, bandCount, builtins,
                    name, selected, presets, fields.containsKey(8));
        }
        return latest;
    }

    private static Map<Integer,byte[]> uniqueFields(byte[] frame) {
        int off = 6, end = frame.length - 2;
        Map<Integer,byte[]> fields = new HashMap<>();
        while (off < end) {
            if (off + 2 > end) return null;
            int type = u(frame[off++]);
            int length = u(frame[off++]);
            if (off + length > end || fields.containsKey(type)) return null;
            fields.put(type, Arrays.copyOfRange(frame, off, off + length));
            off += length;
        }
        return off == end ? fields : null;
    }

    private static List<EqualizerState.Preset> parsePresets(byte[] data) {
        if (data == null || data.length == 0) return Collections.emptyList();
        if (data.length % CUSTOM_RECORD_SIZE != 0) return null;
        List<EqualizerState.Preset> results = new ArrayList<>();
        for (int p = 0; p < data.length; p += CUSTOM_RECORD_SIZE) {
            int id = u(data[p]), bands = u(data[p + 1]);
            if (id < 0x64 || id > 0x66 || bands != 10) return null;
            List<Integer> gains = parseGains(Arrays.copyOfRange(data,p+2,p+12));
            if (gains == null) return null;
            String name = safeName(Arrays.copyOfRange(data,p+12,p+36));
            if (name == null) return null;
            results.add(new EqualizerState.Preset(id, name, gains));
        }
        return results;
    }

    private static List<Integer> parseGains(byte[] raw) {
        List<Integer> gains = new ArrayList<>(raw.length);
        for (byte b : raw) {
            int gain = b; // signed tenth-dB, do not convert with &255
            if (gain < EqualizerState.MIN_GAIN || gain > EqualizerState.MAX_GAIN) return null;
            gains.add(gain);
        }
        return gains;
    }

    private static String safeName(byte[] raw) {
        if (raw == null) return null;
        int length = 0;
        while (length < raw.length && raw[length] != 0) length++;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(raw, 0, length)).toString().trim();
        } catch (CharacterCodingException e) { return null; }
    }

    private static Integer one(byte[] bytes) {
        return bytes != null && bytes.length == 1 ? u(bytes[0]) : null;
    }
    private static int u(byte b) { return b & 255; }
}
