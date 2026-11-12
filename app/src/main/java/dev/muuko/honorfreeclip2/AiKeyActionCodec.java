package dev.muuko.honorfreeclip2;

import java.util.Arrays;
import java.util.Map;

/**
 * FreeClip 2 AI-key state model, grounded in independent left/right 2B/17 A/B captures.
 * Only 0x00 (wake agent) and 0xFF (none) were actually observed.
 * 2B/16 write on the two observed actions was reported successful by the user
 * after an exact 2B/17 CRC-verified readback. Maintain conservative preflight
 * and never infer other action codes from the advertised capability list.
 */
final class AiKeyActionCodec {
    static final int LEFT = 1, RIGHT = 2;
    static final int WAKE_AGENT = 0x00, NONE = 0xFF;
    static final int UNKNOWN = -1;

    private AiKeyActionCodec() { }

    static boolean sideAllowed(int side) { return side == LEFT || side == RIGHT; }
    static boolean actionAllowed(int action) { return action == WAKE_AGENT || action == NONE; }
    static String actionName(int code) {
        if (code == WAKE_AGENT) return "唤醒智能体";
        if (code == NONE) return "无";
        return String.format(java.util.Locale.ROOT,"未知动作 (0x%02X)",code & 255);
    }
    static int value(AiKeyProbeCodec.State state, int side) {
        if (state == null || !sideAllowed(side)) return UNKNOWN;
        return side == LEFT ? state.leftRaw : state.rightRaw;
    }
    /** Require both sides to be a confirmed-value set. Unknown states cannot be overwritten. */
    static boolean readable(AiKeyProbeCodec.State state) {
        return state != null && actionAllowed(state.leftRaw) && actionAllowed(state.rightRaw)
                && state.tlvs.containsKey(1) && state.tlvs.containsKey(2);
    }
    static byte[] verifiedWrite(int side,int action) {
        if (!sideAllowed(side) || !actionAllowed(action)) return null;
        byte[] header = new byte[]{0x5A,0,6,0,0x2B,0x16,(byte)side,1,(byte)action};
        int crc = FreeClipProtocol.crc16(header,header.length);
        return FreeClipProtocol.concat(header,new byte[]{(byte)(crc>>>8),(byte)crc});
    }
    /** Strict snapshot check to avoid racing a setting change from the official app. */
    static boolean sameSnapshot(AiKeyProbeCodec.State old, AiKeyProbeCodec.State fresh) {
        if (!readable(old) || !readable(fresh)) return false;
        if (!old.tlvs.keySet().equals(fresh.tlvs.keySet())) return false;
        for (Map.Entry<Integer,byte[]> item: old.tlvs.entrySet()) {
            if (!Arrays.equals(item.getValue(),fresh.tlvs.get(item.getKey()))) return false;
        }
        return true;
    }
    /** Verify target changed exactly as requested and non-target fields remain unchanged. */
    static boolean verified(AiKeyProbeCodec.State before,AiKeyProbeCodec.State after,int side,int action) {
        if (!readable(before)||!readable(after)||!sideAllowed(side)||!actionAllowed(action)) return false;
        if (value(after,side)!=action) return false;
        if (value(after,side==LEFT?RIGHT:LEFT)!=value(before,side==LEFT?RIGHT:LEFT)) return false;
        if (!before.tlvs.keySet().equals(after.tlvs.keySet())) return false;
        for (Map.Entry<Integer,byte[]> e:before.tlvs.entrySet()) {
            if (e.getKey()==side) continue;
            if (!Arrays.equals(e.getValue(),after.tlvs.get(e.getKey()))) return false;
        }
        return true;
    }
}
