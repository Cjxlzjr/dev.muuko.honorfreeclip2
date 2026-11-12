package dev.muuko.honorfreeclip2;
public final class AiKeyRealFrameSelfTest {
    private static void check(boolean p,String m) {if(!p)throw new AssertionError(m);System.out.println("PASS " + m);}
    public static void main(String[] args) {
        byte[] frame=FreeClipProtocol.hex("5A002B002B170101000201000310000102030405060708090A0E0F1114FF0401FF0501FF060200FF0901000A01000D5C");
        check(frame.length==48,"sample size 48 bytes");
        check(FreeClipProtocol.crc16(frame,frame.length-2)==0x0D5C,"real capture CRC 0D5C");
        AiKeyProbeCodec.State state=AiKeyProbeCodec.parseState(frame);
        check(state!=null,"real capture parsed as 2B17");
        check(state.tlvs.size()==8,"eight real TLVs preserved");
        check(state.tlvs.get(3).length==16,"TLV03 capability-list candidate captured");
        check(AiKeyProbeCodec.compare(state,state).contains("所有字段均未变化"),"all TLVs compared");
        byte[] changed=frame.clone(); changed[changed.length-4] ^= 1;
        check(AiKeyProbeCodec.parseState(changed)==null,"bad CRC rejected");
    }
}
