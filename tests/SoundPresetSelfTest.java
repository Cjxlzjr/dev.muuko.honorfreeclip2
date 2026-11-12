package dev.muuko.honorfreeclip2;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class SoundPresetSelfTest {
    private static void ok(boolean pass,String label) {
        if(!pass) throw new AssertionError(label);
        System.out.println("PASS " + label);
    }
    private static byte[] tlv(int tag,byte... values) {
        return FreeClipProtocol.concat(new byte[]{(byte)tag,(byte)values.length},values);
    }
    private static byte[] frame(byte[] body) {
        byte[] head=new byte[body.length+4];head[0]=0x5a;
        head[2]=(byte)(body.length+1);head[3]=(byte)((body.length+1)>>>8);
        System.arraycopy(body,0,head,4,body.length);
        int crc=FreeClipProtocol.crc16(head,head.length);
        return FreeClipProtocol.concat(head,new byte[]{(byte)(crc>>>8),(byte)crc});
    }
    public static void main(String[] ignored) {
        EqualizerState custom = new EqualizerState(true,0x64,10, Arrays.asList(1,10,3,9),
              "八音盒",Arrays.asList(-12,4,0,8,7,0,0,0,0,0),Collections.emptyList());
        ok("八音盒（自定义）".equals(SoundPresetCatalog.currentLabel(0x64,custom)),"custom id labeled instead of unread");
        ok(SoundPresetCatalog.writableOptions(custom).size()==4,"four verified builtin IDs advertised");
        ok(SoundPresetCatalog.canWrite(custom,9) && !SoundPresetCatalog.canWrite(custom,100),"builtin writing only");
        ok(SoundPresetCatalog.samePreflight(custom,custom),"same custom snapshot preflight");
        EqualizerState changed = new EqualizerState(true,0x64,10,Arrays.asList(1,10,3,9),
              "八音盒",Arrays.asList(-10,4,0,8,7,0,0,0,0,0),Collections.emptyList());
        ok(!SoundPresetCatalog.samePreflight(custom,changed),"external custom EQ changes rejected");
        EqualizerState sparse = new EqualizerState(true,1,10,Collections.emptyList(),null,null,Collections.emptyList());
        ok(SoundPresetCatalog.writableOptions(sparse).size()==4,"missing optional built-in catalog uses four FreeClip2 verified IDs");
        EqualizerState limited = new EqualizerState(true,9,10,Arrays.asList(1,9,17),null,null,Collections.emptyList());
        ok(SoundPresetCatalog.writableOptions(limited).size()==2 && !SoundPresetCatalog.canWrite(limited,10),"device advertisement takes priority");
        byte[] eq = frame(FreeClipProtocol.concat(new byte[]{0x2B,0x4A},tlv(1,(byte)1),tlv(2,(byte)0x64),tlv(3,(byte)1,(byte)10,(byte)3,(byte)9),tlv(5,(byte)10)));
        EqualizerState parsed = EqualizerCodec.parse(eq);
        ok(parsed != null && parsed.selectedId==0x64,"detailed EQ state parses custom selection");
        ok(FreeClipProtocol.decode(eq).get(FreeClipSetting.SOUND_EFFECT)==0x64,"selected custom ID no longer lost in basic decoding");
        byte[] corruption=eq.clone();corruption[corruption.length-1]^=1;
        ok(!FreeClipProtocol.decode(corruption).containsKey(FreeClipSetting.SOUND_EFFECT),"CRC corruption not accepted");
        // A TLV value can contain 02 01; must not be misinterpreted as selectedID.
        byte[] spoof = frame(FreeClipProtocol.concat(new byte[]{0x2B,0x4A},tlv(7,(byte)2,(byte)1,(byte)9),tlv(2,(byte)3)));
        ok(FreeClipProtocol.decode(spoof).get(FreeClipSetting.SOUND_EFFECT)==3,"proper TLV boundaries used");
    }
}
