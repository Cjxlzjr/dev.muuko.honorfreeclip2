package dev.muuko.honorfreeclip2;

import java.util.Arrays;
import java.util.Random;
import java.util.Map;

public final class ProtocolRobustnessSelfTest {
    static byte[] frame(int... body) {
        byte[] head = new byte[4 + body.length];
        head[0] = 0x5A;
        head[2] = (byte)(body.length + 1);
        for (int i=0;i<body.length;i++) head[4+i]=(byte)body[i];
        int crc = FreeClipProtocol.crc16(head,head.length);
        return FreeClipProtocol.concat(head,new byte[]{(byte)(crc>>>8),(byte)crc});
    }
    static void check(boolean ok,String label) {
        if (!ok) throw new AssertionError(label);
        System.out.println("PASS "+label);
    }
    public static void main(String[] args) {
        check(FreeClipProtocol.decode(frame(0x2B,0xB4,1,1,0x18,2,1,2,3,1,1,2,1,0)).isEmpty(),
                "CRC-valid duplicate spatial TLV rejected");
        Map<FreeClipSetting,Integer> reordered = FreeClipProtocol.decode(
                frame(0x2B,0xB4,3,1,1,2,1,2,1,1,0x18));
        check(reordered.get(FreeClipSetting.SPATIAL_MODE)==2 && reordered.get(FreeClipSetting.SPATIAL_SCENE)==1,
                "reordered valid spatial TLVs decoded");
        check(FreeClipProtocol.decode(frame(0x2B,0xB4,1,1,0x18,2,2,1)).isEmpty(),
                "truncated spatial TLV rejected");
        check(FreeClipProtocol.decode(frame(0x2B,0x11,1,1,4)).isEmpty(),"unknown boolean remains read-only");
        byte[] good=frame(0x2B,0x11,1,1,1);
        check(FreeClipProtocol.decode(Arrays.copyOf(good,good.length-1)).isEmpty(),"fragment not accepted as state");
        good[good.length-1]^=1;
        check(FreeClipProtocol.decode(good).isEmpty(),"bad CRC rejected");
        check(FreeClipProtocol.decode(new byte[65537]).isEmpty(),"oversized stream rejected");
        // rebuild full valid TLV catalog containing the same slot twice
        int[] duplicate=new int[5+2+72];
        duplicate[0]=0x2B;duplicate[1]=0x4A;duplicate[2]=2;duplicate[3]=1;duplicate[4]=100;
        duplicate[5]=8;duplicate[6]=72;
        for(int at:new int[]{7,43}) { duplicate[at]=100;duplicate[at+1]=10;duplicate[at+12]='A'; }
        check(EqualizerCodec.parse(frame(duplicate))==null,"duplicate custom EQ slots rejected");
        Random random=new Random(17);
        for(int n=0;n<3000;n++) {
            byte[] noise=new byte[random.nextInt(600)];random.nextBytes(noise);
            FreeClipProtocol.decode(noise);EqualizerCodec.parse(noise);AiKeyProbeCodec.parseState(noise);
        }
        check(true,"3000 malformed streams handled without exceptions");
    }
}
