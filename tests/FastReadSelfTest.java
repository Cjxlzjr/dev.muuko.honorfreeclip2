package dev.muuko.honorfreeclip2;

/** Non-Android regression tests for the conservative query idle gate. */
public final class FastReadSelfTest {
    static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        System.out.println("PASS " + label);
    }
    static byte[] reply(int... body) {
        byte[] buffer = new byte[body.length + 4];
        buffer[0] = 0x5A; buffer[2] = (byte)(body.length + 1);
        for (int i = 0; i < body.length; i++) buffer[4 + i] = (byte)body[i];
        int crc = FreeClipProtocol.crc16(buffer, buffer.length);
        return FreeClipProtocol.concat(buffer, new byte[]{(byte)(crc >>> 8), (byte)crc});
    }
    public static void main(String[] args) {
        byte[] query = FreeClipProtocol.queryFor(FreeClipSetting.DROP);
        byte[] value = reply(0x2B, 0xB4, 1, 1, 7, 2, 1, 1);
        check(!ReadIdlePolicy.ready(query, value, 100), "do not exit before quiet time");
        check(ReadIdlePolicy.ready(query, value, 260), "complete single CRC response exits early");
        byte[] invalid = value.clone(); invalid[invalid.length - 1] ^= 1;
        check(!ReadIdlePolicy.ready(query, invalid, 700), "CRC corruption blocks early completion");
        check(!ReadIdlePolicy.ready(query, new byte[]{0x5A,0,6,0,0x2B}, 800), "fragment cannot finish");
        byte[] ackOnly = reply(0x2B, 0x10, 1, 1, 1);
        check(!ReadIdlePolicy.ready(query, ackOnly, 700), "ACK cannot finish state query");
        check(!ReadIdlePolicy.ready(FreeClipProtocol.GESTURE_QUERIES, value, 400), "batch requires longer idle");
        check(ReadIdlePolicy.ready(FreeClipProtocol.GESTURE_QUERIES, value, 620), "batch with decoded state and quiet can finish");
        check(!ReadIdlePolicy.ready(EqualizerCodec.QUERY, value, 620), "EQ-only query requires detailed EQ report");
        check(!ReadIdlePolicy.ready(AiKeyProbeCodec.QUERY, value, 620), "AI-only query requires verified AI frame");
    }
}
