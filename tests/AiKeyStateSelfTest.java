package dev.muuko.honorfreeclip2;
public final class AiKeyStateSelfTest {
    private static void ok(boolean yes,String msg){if(!yes)throw new AssertionError(msg);System.out.println("PASS "+msg);}
    private static byte[] frame(byte[] body) {
        byte[] h=new byte[body.length+4];h[0]=0x5a;h[2]=(byte)(body.length+1);
        h[3]=(byte)((body.length+1)>>>8);System.arraycopy(body,0,h,4,body.length);
        int c=FreeClipProtocol.crc16(h,h.length);
        return FreeClipProtocol.concat(h,new byte[]{(byte)(c>>>8),(byte)c});
    }
    public static void main(String[] ignored){
        byte[] valid=frame(new byte[]{0x2B,0x17,1,1,0,2,1,3});
        AiKeyProbeCodec.State raw=AiKeyProbeCodec.parseState(valid);
        ok(raw!=null && raw.leftRaw==0 && raw.rightRaw==3,"left/right raw candidates detected");
        ok(raw.summary().contains("唤醒智能体") && raw.summary().contains("未知动作"),"map captured actions but keep unknown raw");
        AiKeyProbeCodec.State after=new AiKeyProbeCodec.State(2,3,"2B17");
        ok(AiKeyProbeCodec.compare(raw,after).contains("TLV01 0x00 → 0x02"),"candidate change is displayed");
        ok(AiKeyProbeCodec.compare(raw,raw).contains("未变化"),"no change reported explicitly");
        byte[] changedTlvs=frame(new byte[]{0x2B,0x17,1,1,0,2,1,3,3,2,0x17,0x18});
        byte[] sameCandidates=frame(new byte[]{0x2B,0x17,1,1,0,2,1,3,3,2,0x17,0x19});
        AiKeyProbeCodec.State tlvBefore=AiKeyProbeCodec.parseState(changedTlvs);
        AiKeyProbeCodec.State tlvAfter=AiKeyProbeCodec.parseState(sameCandidates);
        ok(tlvBefore!=null && tlvAfter!=null,"parse full valid TLV frame");
        ok(AiKeyProbeCodec.compare(tlvBefore,tlvAfter).contains("TLV03: 1718 → 1719"),
                "changes outside TLV01/02 are detected");
        ok(AiKeyProbeCodec.compare(tlvBefore,tlvBefore).contains("所有字段均未变化"),
                "full frame stability clearly reported");
        byte[] bad=valid.clone();bad[bad.length-1]^=1;
        ok(AiKeyProbeCodec.parseState(bad)==null,"invalid CRC rejected");
        byte[] duplicate=frame(new byte[]{0x2B,0x17,1,1,0,1,1,1,2,1,0});
        ok(AiKeyProbeCodec.parseState(duplicate)==null,"duplicate TLVs rejected");
        byte[] incomplete=frame(new byte[]{0x2B,0x17,1,1,0});
        ok(AiKeyProbeCodec.parseState(incomplete)==null,"incomplete right slot rejected");
        byte[] mixed=FreeClipProtocol.concat(bad,valid);
        ok(AiKeyProbeCodec.parseState(mixed)!=null,"recover valid state after corrupted frame");
    }
}
