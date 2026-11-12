package dev.muuko.honorfreeclip2;

import java.util.Arrays;

/** Conservative early-completion heuristic for CRC-validated state replies only. */
final class ReadIdlePolicy {
    private static final long SINGLE_IDLE_MS = 240;
    private static final long GROUP_IDLE_MS = 600;
    private ReadIdlePolicy() { }

    static boolean ready(byte[] query, byte[] reply, long quietMs) {
        if (query == null || reply == null || reply.length < 9) return false;
        if (Arrays.equals(query, FreeClipProtocol.ALL_QUERIES)) {
            // A complete three-family batch can finish quickly. For partial batches,
            // a longer quiet period allows late replies before selective recovery.
            java.util.Map<FreeClipSetting,Integer> decoded = FreeClipProtocol.decode(reply);
            EqualizerState eq = EqualizerCodec.parse(reply);
            if (ReadBatchPolicy.allGroupsResponded(decoded, eq)) return quietMs >= 180;
            return quietMs >= 460 && (!decoded.isEmpty() || eq != null);
        }
        boolean eqOnly = Arrays.equals(query, EqualizerCodec.QUERY);
        boolean aiOnly = Arrays.equals(query, AiKeyProbeCodec.QUERY);
        boolean batched = query.length > 24;
        long quietNeeded = batched ? GROUP_IDLE_MS : SINGLE_IDLE_MS;
        if (quietMs < quietNeeded) return false;
        if (eqOnly) return EqualizerCodec.parse(reply) != null;
        if (aiOnly) return AiKeyProbeCodec.parseState(reply) != null;
        // Non-empty decoding guarantees a CRC-valid settings frame, not just an ACK.
        return !FreeClipProtocol.decode(reply).isEmpty() || EqualizerCodec.parse(reply) != null;
    }

    /** A write is acknowledged only by the exact target setting, never any other report. */
    static boolean readyForSetting(FreeClipSetting key, byte[] reply, long quietMs) {
        return key != null && reply != null && quietMs >= 105
                && FreeClipProtocol.decode(reply).containsKey(key);
    }
}
