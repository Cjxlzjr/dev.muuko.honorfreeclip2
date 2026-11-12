package dev.muuko.honorfreeclip2;
import java.util.Arrays;
import java.util.Map;

/** Pure Java verification of packet generation, CRC and state readback parsing. */
public final class ProtocolSelfTest {
    private static byte[] response(int... body) {
        int size = body.length + 1;
        byte[] head = new byte[4 + body.length];
        head[0] = 0x5A; head[2] = (byte) size;
        for (int i = 0; i < body.length; i++) head[i + 4] = (byte)body[i];
        int crc = FreeClipProtocol.crc16(head, head.length);
        return FreeClipProtocol.concat(head, new byte[]{(byte)(crc >> 8), (byte)crc});
    }
    private static void check(boolean good, String name) {
        if (!good) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }
    public static void main(String[] args) {
        check(FreeClipProtocol.crc16(FreeClipProtocol.hex("5A0006002B10010101"), 9) == 0xA956,
                "Huawei CRC16/XMODEM WEAR on");
        for (FreeClipSetting f : FreeClipSetting.values()) {
            if (!f.supported) continue;
            for (int v : f.isBoolean ? new int[]{0,1} : f.values) {
                byte[] packet = FreeClipProtocol.writePacket(f, v);
                check(packet != null && packet.length >= 9 &&
                        FreeClipProtocol.crc16(packet, packet.length - 2)
                                == (((packet[packet.length - 2]&255)<<8)|(packet[packet.length - 1]&255)),
                        "write packet valid " + f.name() + "=" + v);
            }
        }
        byte[] drop = response(0x2B,0xB4, 1,1,7, 2,1,1);
        check(FreeClipProtocol.decode(drop).get(FreeClipSetting.DROP) == 1, "drop readback");
        byte[] headMotion = response(0x2B,0xB4,1,1,0x0B,2,1,1,3,1,1,4,1,2);
        Map<FreeClipSetting,Integer> motion=FreeClipProtocol.decode(headMotion);
        check(motion.get(FreeClipSetting.HEAD_MOTION)==1
                && motion.get(FreeClipSetting.HEAD_NOD)==1
                && motion.get(FreeClipSetting.HEAD_SHAKE)==2, "head motion readback");
        byte[] sound = response(0x2B,0x4A,2,1,0x0A);
        check(FreeClipProtocol.decode(sound).get(FreeClipSetting.SOUND_EFFECT)==10,
                "sound-effect readback");
        byte[] noise = response(0x2B,0x88,1,1,0);
        check(FreeClipProtocol.decode(noise).get(FreeClipSetting.NOISY)==0,
                "noisy-volume readback");
        byte[] spatial = response(0x2B,0xB4,1,1,0x18,2,1,2,3,1,3);
        Map<FreeClipSetting,Integer> audio=FreeClipProtocol.decode(spatial);
        check(audio.get(FreeClipSetting.SPATIAL_MODE) == 2 && audio.get(FreeClipSetting.SPATIAL_SCENE)==3,
                "spatial audio mode/scene readback");
        byte[] taps = response(1,0x20,1,1,1,2,1,2);
        Map<FreeClipSetting,Integer> gestures=FreeClipProtocol.decode(taps);
        check(gestures.get(FreeClipSetting.DOUBLE_LEFT)==1 && gestures.get(FreeClipSetting.DOUBLE_RIGHT)==2,
                "gesture left and right readback");
        drop[drop.length-1] ^= 1;
        check(FreeClipProtocol.decode(drop).isEmpty(), "corrupt CRC rejected");
        check(!FreeClipSetting.CUSTOM_EQ.supported && !FreeClipSetting.LONG_PRESS.supported,
                "unsupported controls cannot write");
        check(FreeClipProtocol.writePacket(FreeClipSetting.HEAD_NOD, 2).length == 14,
                "head-motion action packet framing");
    }
}
