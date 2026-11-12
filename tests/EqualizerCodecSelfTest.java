package dev.muuko.honorfreeclip2;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Reference-derived CRC, TLV and 10-band state tests, no Android dependencies. */
public final class EqualizerCodecSelfTest {
    private static void check(boolean okay,String label) {
        if (!okay) throw new AssertionError(label);
        System.out.println("PASS " + label);
    }
    private static byte[] frame(byte[] body) {
        byte[] header = new byte[body.length+4];
        header[0] = 0x5A;
        header[2] = (byte)(body.length+1);
        header[3] = (byte)((body.length+1)>>>8);
        System.arraycopy(body,0,header,4,body.length);
        int crc = FreeClipProtocol.crc16(header,header.length);
        return FreeClipProtocol.concat(header,new byte[]{(byte)(crc>>>8),(byte)crc});
    }
    private static byte[] fields(byte[]... arrays) {return FreeClipProtocol.concat(arrays);}
    private static byte[] tlv(int type,byte[] bytes) {
        return FreeClipProtocol.concat(new byte[]{(byte)type,(byte)bytes.length},bytes);
    }
    private static byte[] customRecord(int id,String name,int... gains) {
        byte[] raw = new byte[36];
        raw[0]=(byte)id;raw[1]=10;
        for (int i=0;i<10;i++) raw[i+2]=(byte)gains[i];
        byte[] nameBytes=name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes,0,raw,12,Math.min(nameBytes.length,24));
        return raw;
    }
    public static void main(String[] args) {
        int[] gains={-60,-25,-5,0,0,1,10,20,40,60};
        byte[] curve = new byte[10]; for (int i=0;i<10;i++) curve[i]=(byte)gains[i];
        byte[] record=customRecord(0x64,"我的音效 1",gains);
        byte[] body=fields(new byte[]{0x2B,0x4A},
                tlv(1,new byte[]{1}),tlv(2,new byte[]{0x64}),
                tlv(3,new byte[]{1,3,9}),tlv(5,new byte[]{10}),
                tlv(6,curve),tlv(7,"我的音效 1".getBytes(StandardCharsets.UTF_8)),
                tlv(8,record));
        byte[] report=frame(body);
        EqualizerState eq=EqualizerCodec.parse(report);
        check(eq!=null,"valid EQ frame parsed");
        check(eq.supported && eq.isCustom(),"supported custom selected");
        check(eq.hasTenBandCurve() && eq.selectedGains.get(0)==-60 &&
                eq.selectedGains.get(9)==60,"signed tenth-dB gains preserve range");
        check(eq.customPresets.size()==1 && "我的音效 1".equals(eq.selectedName),
                "custom preset name and ID");
        check(eq.builtInIds.size()==3 && eq.builtInIds.get(2)==9,"built-in preset IDs");
        byte[] corrupted=report.clone();corrupted[corrupted.length-1]^=1;
        check(EqualizerCodec.parse(corrupted)==null,"CRC corruption rejected");
        byte[] badGain=record.clone();badGain[2]=61;
        byte[] invalid=frame(fields(new byte[]{0x2B,0x4A},tlv(1,new byte[]{1}),
                tlv(2,new byte[]{0x64}),tlv(8,badGain)));
        check(EqualizerCodec.parse(invalid)==null,"out-of-range custom gain rejected");
        byte[] badCount=record.clone();badCount[1]=9;
        check(EqualizerCodec.parse(frame(fields(new byte[]{0x2B,0x4A},
                tlv(2,new byte[]{0x64}),tlv(8,badCount))))==null,
                "wrong custom band count rejected");
        byte[] repeated=frame(fields(new byte[]{0x2B,0x4A},
                tlv(2,new byte[]{0x64}),tlv(2,new byte[]{0x64})));
        check(EqualizerCodec.parse(repeated)==null,"duplicate TLVs rejected");
        byte[] single=frame(fields(new byte[]{0x2B,0x4A},
                tlv(1,new byte[]{1}),tlv(2,new byte[]{1}),tlv(6,curve)));
        EqualizerState builtIn=EqualizerCodec.parse(single);
        check(builtIn!=null && !builtIn.isCustom() && builtIn.selectedGains.size()==10,
                "built-in curve readback");
        check(EqualizerCodec.parse(FreeClipProtocol.concat(corrupted,report))!=null,
                "scanner recovers after corrupted preceding frame");
        check(EqualizerCodec.parse(null)==null && EqualizerCodec.parse(new byte[0])==null,
                "null and empty rejected");
        check(!FreeClipSetting.CUSTOM_EQ.supported && !FreeClipSetting.LONG_PRESS.supported,
                "unverified writes remain disabled");
    }
}
