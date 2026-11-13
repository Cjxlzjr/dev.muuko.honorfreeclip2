package dev.muuko.honorfreeclip2;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Selected FreeClip 2 device controls ported from Nshpiter/HuaweiPods (GPL-3.0).
 * Do not interpret RFCOMM ACK or local preference state as a successful write.
 * CRC16/XMODEM is validated on every received frame before state decoding.
 */
final class FreeClipProtocol {
    static final byte[] BOOL_QUERIES = concat(
            hex("5A0005002B110100772A"),
            hex("5A0008002BB40101070200DDE9"),
            hex("5A0008002BB401010202003619"),
            hex("5A0006002BB401010B289B"),
            hex("5A0005002B8801009182"),
            hex("5A0005002B6C0200B820"),
            hex("5A0005002B2F0100A98E"),
            hex("5A0007002BB1020003007FAB"));
    static final byte[] AUDIO_QUERIES = concat(
            hex("5A000A002BB4010118020003009B3F"),
            hex("5A0005002B4A02008C46"));
    static final byte[] GESTURE_QUERIES = concat(
            hex("5A000700012001000200E897"),
            hex("5A0007000126010002002512"),
            hex("5A0007002B1F01000200328A"));
    /** Single SPP volley reduces three independent quiet windows to one. */
    static final byte[] ALL_QUERIES = concat(BOOL_QUERIES, AUDIO_QUERIES, GESTURE_QUERIES);

    private FreeClipProtocol() { }

    static byte[] queryFor(FreeClipSetting item) {
        switch (item) {
            case SPATIAL_MODE: case SPATIAL_SCENE:
                return hex("5A000A002BB4010118020003009B3F");
            case SOUND_EFFECT:
                return hex("5A0005002B4A02008C46");
            case DOUBLE_LEFT: case DOUBLE_RIGHT:
                return hex("5A000700012001000200E897");
            case TRIPLE_LEFT: case TRIPLE_RIGHT:
                return hex("5A0007000126010002002512");
            case SWIPE_LEFT: case SWIPE_RIGHT:
                return hex("5A0007002B1F01000200328A");
            case HEAD_MOTION: case HEAD_NOD: case HEAD_SHAKE:
                return hex("5A0006002BB401010B289B");
            case WEAR: return hex("5A0005002B110100772A");
            case DROP: return hex("5A0008002BB40101070200DDE9");
            case ADAPTIVE: return hex("5A0008002BB401010202003619");
            case NOISY: return hex("5A0005002B8801009182");
            case LOW_LATENCY: return hex("5A0005002B6C0200B820");
            case DUAL_DEVICE: return hex("5A0005002B2F0100A98E");
            case CASE_SOUND: return hex("5A0007002BB1020003007FAB");
            default: return new byte[0];
        }
    }

    static byte[] writePacket(FreeClipSetting item, int value) {
        if (!item.accepts(value)) return null;
        boolean on = value == 1;
        switch (item) {
            case WEAR: return hex(on ? "5A0006002B10010101A956" : "5A0006002B10010100B977");
            case DROP: return hex(on ? "5A0009002BB4010107020101BF85" : "5A0009002BB4010107020100AFA4");
            case ADAPTIVE: return hex(on ? "5A0009002BB401010202010103C0" : "5A0009002BB401010202010013E1");
            case HEAD_MOTION: return hex(on ? "5A0009002BB401010B020101F0B7" : "5A0009002BB401010B020100E096");
            case NOISY: return hex(on ? "5A0006002B870101013EE4" : "5A0006002B870101002EC5");
            case LOW_LATENCY: return hex(on ? "5A0006002B6C010101A411" : "5A0006002B6C010100B430");
            case DUAL_DEVICE: return hex(on ? "5A0006002B2E01010127E5" : "5A0006002B2E01010037C4");
            case CASE_SOUND: return hex(on ? "5A0006002BB10101013594" : "5A0006002BB101010025B5");
            case SPATIAL_MODE:
                switch (value) {
                    case 0: return hex("5A0009002BB401011802010060ED");
                    case 1: return hex("5A0009002BB401011802010170CC");
                    case 2: return hex("5A0009002BB401011802010240AF");
                    default: return null;
                }
            case SPATIAL_SCENE:
                switch (value) {
                    case 0: return hex("5A0009002BB401011803010057DD");
                    case 1: return hex("5A0009002BB401011803010147FC");
                    case 2: return hex("5A0009002BB4010118030102779F");
                    case 3: return hex("5A0009002BB401011803010367BE");
                    default: return null;
                }
            case SOUND_EFFECT:
                switch (value) {
                    case 1: return hex("5A0006002B490101012F1A");
                    case 10: return hex("5A0006002B4901010A9E71");
                    case 3: return hex("5A0006002B490101030F58");
                    case 9: return hex("5A0006002B49010109AE12");
                    default: return null;
                }
            case HEAD_NOD: case HEAD_SHAKE:
                return makePacket(0x2B, 0xB4, 1, 1, 0x0B,
                        item == FreeClipSetting.HEAD_NOD ? 3 : 4, 1, value);
            case DOUBLE_LEFT: case DOUBLE_RIGHT:
                return gesturePacket(1, 0x1F, left(item) ? 1 : 2, value);
            case TRIPLE_LEFT: case TRIPLE_RIGHT:
                return gesturePacket(1, 0x25, left(item) ? 1 : 2, value);
            case SWIPE_LEFT: case SWIPE_RIGHT:
                return gesturePacket(0x2B, 0x1E, left(item) ? 1 : 2, value);
            default: return null;
        }
    }

    private static boolean left(FreeClipSetting s) {
        return s == FreeClipSetting.DOUBLE_LEFT || s == FreeClipSetting.TRIPLE_LEFT
                || s == FreeClipSetting.SWIPE_LEFT;
    }

    private static byte[] gesturePacket(int service, int command, int side, int value) {
        return makePacket(service, command, side, 1, value);
    }

    private static byte[] makePacket(int... payload) {
        int length = payload.length + 1; // final body bytes + two CRC bytes
        byte[] noCrc = new byte[4 + payload.length];
        noCrc[0] = 0x5A;
        noCrc[1] = 0;
        noCrc[2] = (byte) length;
        noCrc[3] = (byte) (length >> 8);
        for (int i = 0; i < payload.length; i++) noCrc[4 + i] = (byte) payload[i];
        int crc = crc16(noCrc, noCrc.length);
        return concat(noCrc, new byte[]{(byte) (crc >> 8), (byte) crc});
    }

    static Map<FreeClipSetting, Integer> decode(byte[] stream) {
        EnumMap<FreeClipSetting, Integer> states = new EnumMap<>(FreeClipSetting.class);
        for (byte[] frame : frames(stream)) {
            if (frame.length < 9) continue;
            int service = u(frame[4]), cmd = u(frame[5]);
            Map<Integer, byte[]> fields = fields(frame);
            if (service == 0x2B && cmd == 0xB4) {
                Integer feature = first(fields, 1);
                if (feature != null) {
                    if (feature == 2) putBoolean(states, FreeClipSetting.ADAPTIVE, first(fields, 2));
                    if (feature == 7) putBoolean(states, FreeClipSetting.DROP, first(fields, 2));
                    if (feature == 0x0B) {
                        putBoolean(states, FreeClipSetting.HEAD_MOTION, first(fields, 2));
                        putIfAccepted(states, FreeClipSetting.HEAD_NOD, first(fields, 3));
                        putIfAccepted(states, FreeClipSetting.HEAD_SHAKE, first(fields, 4));
                    }
                }
                // Decode validated TLVs; embedded signatures and duplicate fields are invalid.
                if (feature != null && feature == 0x18) {
                    putIfAccepted(states, FreeClipSetting.SPATIAL_MODE, first(fields, 2));
                    putIfAccepted(states, FreeClipSetting.SPATIAL_SCENE, first(fields, 3));
                }
            }
            if (service == 0x2B && cmd == 0x11)
                putBoolean(states, FreeClipSetting.WEAR, first(fields, 1));
            if (service == 0x2B && cmd == 0x2F)
                putBoolean(states, FreeClipSetting.DUAL_DEVICE, first(fields, 1));
            if (service == 0x2B && cmd == 0x6C)
                putBoolean(states, FreeClipSetting.LOW_LATENCY, first(fields, 2));
            if (service == 0x2B && cmd == 0xB1)
                putBoolean(states, FreeClipSetting.CASE_SOUND, first(fields, 2));
            if (service == 0x2B && cmd == 0x88)
                putBoolean(states, FreeClipSetting.NOISY, first(fields, 1));
            if (service == 0x2B && cmd == 0x4A) {
                // Do not substring-search for "02 01": another TLV payload may contain
                // the same bytes. This fallback only reads the selected ID. The detailed
                // EqualizerCodec parser is authoritative when available.
                Integer selected = first(fields, 2);
                if (selected != null && (SoundPresetCatalog.supportedBuiltIn(selected)
                        || SoundPresetCatalog.isCustom(selected)))
                    states.put(FreeClipSetting.SOUND_EFFECT, selected);
            }
            if (service == 1 && cmd == 0x20) {
                putIfAccepted(states, FreeClipSetting.DOUBLE_LEFT, first(fields, 1));
                putIfAccepted(states, FreeClipSetting.DOUBLE_RIGHT, first(fields, 2));
            }
            if (service == 1 && cmd == 0x26) {
                putIfAccepted(states, FreeClipSetting.TRIPLE_LEFT, first(fields, 1));
                putIfAccepted(states, FreeClipSetting.TRIPLE_RIGHT, first(fields, 2));
            }
            if (service == 0x2B && cmd == 0x1F) {
                putIfAccepted(states, FreeClipSetting.SWIPE_LEFT, first(fields, 1));
                putIfAccepted(states, FreeClipSetting.SWIPE_RIGHT, first(fields, 2));
            }
        }
        return states;
    }

    private static void putBoolean(Map<FreeClipSetting, Integer> output,
                                   FreeClipSetting item, Integer value) {
        if (value != null && (value == 0 || value == 1)) output.put(item, value);
    }

    private static void putIfAccepted(Map<FreeClipSetting, Integer> output,
                                      FreeClipSetting item, Integer value) {
        if (value != null && item.accepts(value)) output.put(item, value);
    }

    private static Integer first(Map<Integer, byte[]> fields, int id) {
        byte[] value = fields.get(id);
        return value != null && value.length == 1 ? u(value[0]) : null;
    }

    /** Empty map for malformed frames; never substitute locally requested state. */
    private static Map<Integer, byte[]> fields(byte[] frame) {
        Map<Integer, byte[]> out = new HashMap<>();
        int offset = 6;
        int end = frame.length - 2;
        while (offset < end) {
            if (offset + 2 > end) return new HashMap<>();
            int id = u(frame[offset++]);
            int len = u(frame[offset++]);
            if (offset + len > end || out.containsKey(id)) return new HashMap<>();
            out.put(id, Arrays.copyOfRange(frame, offset, offset + len));
            offset += len;
        }
        return out;
    }

    private static List<byte[]> frames(byte[] stream) {
        List<byte[]> result = new ArrayList<>();
        if (stream == null || stream.length > 64 * 1024) return result;
        int offset = 0;
        while (offset + 9 <= stream.length) {
            if (u(stream[offset]) != 0x5A || stream[offset + 1] != 0) { offset++; continue; }
            int size = 5 + u(stream[offset + 2]) + (u(stream[offset + 3]) << 8);
            if (size < 9 || size > 4096 || offset + size > stream.length) { offset++; continue; }
            byte[] frame = Arrays.copyOfRange(stream, offset, offset + size);
            int expected = crc16(frame, frame.length - 2);
            if (u(frame[size - 2]) != (expected >> 8) || u(frame[size - 1]) != (expected & 255)) {
                offset++;
                continue;
            }
            result.add(frame);
            offset += size;
        }
        return result;
    }

    static int crc16(byte[] bytes, int length) {
        int crc = 0;
        for (int i = 0; i < length; i++) {
            crc ^= u(bytes[i]) << 8;
            for (int j = 0; j < 8; j++)
                crc = ((crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1) & 0xFFFF;
        }
        return crc;
    }

    static byte[] concat(byte[]... data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : data) out.write(part, 0, part.length);
        return out.toByteArray();
    }

    static byte[] hex(String h) {
        if (h.length() % 2 != 0) throw new IllegalArgumentException("odd hex string");
        byte[] bytes = new byte[h.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }

    private static int indexOf(byte[] a, byte[] b, int start) {
        for (int i = start; i <= a.length - b.length; i++) {
            boolean match = true;
            for (int j = 0; j < b.length; j++) if (a[i + j] != b[j]) { match = false; break; }
            if (match) return i;
        }
        return -1;
    }

    private static int u(byte b) { return b & 255; }
}
