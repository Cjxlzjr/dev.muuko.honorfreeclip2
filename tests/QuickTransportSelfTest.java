package dev.muuko.honorfreeclip2;

import java.util.EnumMap;

/** Host-side guards for selective fallback, idle socket reuse and exact-setting readback. */
public final class QuickTransportSelfTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }

    private static byte[] reply(int... body) {
        byte[] buffer = new byte[body.length + 4];
        buffer[0] = 0x5A; buffer[2] = (byte)(body.length + 1);
        for (int i = 0; i < body.length; i++) buffer[4+i] = (byte)body[i];
        int crc = FreeClipProtocol.crc16(buffer, buffer.length);
        return FreeClipProtocol.concat(buffer, new byte[]{(byte)(crc >>> 8), (byte)crc});
    }

    public static void main(String[] args) {
        EnumMap<FreeClipSetting,Integer> set = new EnumMap<>(FreeClipSetting.class);
        check(!ReadBatchPolicy.hasBoolean(set), "empty batch cannot confirm boolean group");
        set.put(FreeClipSetting.WEAR, 1);
        check(ReadBatchPolicy.hasBoolean(set) && ReadBatchPolicy.booleanCount(set)==1,
                "boolean group detects partial reply");
        set.put(FreeClipSetting.DROP, 1);
        set.put(FreeClipSetting.ADAPTIVE, 1);
        set.put(FreeClipSetting.DOUBLE_LEFT, 1);
        set.put(FreeClipSetting.TRIPLE_LEFT, 2);
        set.put(FreeClipSetting.SPATIAL_MODE, 1);
        check(ReadBatchPolicy.gestureCount(set)==2, "gesture group counts separately");
        check(!ReadBatchPolicy.allGroupsResponded(set, null), "EQ state is required for fastest combined completion");
        check(SocketReusePolicy.eligible("AA", "AA", true, false, 100, false), "short idle can reuse");
        check(!SocketReusePolicy.eligible("AA", "BB", true, false, 100, false), "other device cannot reuse");
        check(!SocketReusePolicy.eligible("AA", "AA", true, true, 100, false), "borrowed connection cannot double-use");
        check(!SocketReusePolicy.eligible("AA", "AA", true, false,
                SocketReusePolicy.IDLE_TTL_MS, false), "expired connection cannot reuse");
        check(!SocketReusePolicy.eligible("AA", "AA", true, false, 200, true), "pause closes connection");
        byte[] drop = reply(0x2B, 0xB4, 1, 1, 7, 2, 1, 1);
        check(ReadIdlePolicy.readyForSetting(FreeClipSetting.DROP, drop, 110), "exact target response completes fast");
        check(!ReadIdlePolicy.readyForSetting(FreeClipSetting.WEAR, drop, 600), "unrelated response cannot confirm write");
        check(!ReadIdlePolicy.readyForSetting(FreeClipSetting.DROP, drop, 55), "too-early response waits for quiet");
        byte[] bad = drop.clone(); bad[bad.length-1]^=3;
        check(!ReadIdlePolicy.readyForSetting(FreeClipSetting.DROP, bad, 600), "bad CRC cannot confirm write");
    }
}
