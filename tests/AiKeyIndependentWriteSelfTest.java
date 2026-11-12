package dev.muuko.honorfreeclip2;

import java.util.Arrays;

/** User-provided FreeClip 2 2B/17 A/B state captures; 2B/16 encode is EXPERIMENTAL. */
public final class AiKeyIndependentWriteSelfTest {
    static void check(boolean condition,String msg) {
        if(!condition) throw new AssertionError(msg);
        System.out.println("PASS " + msg);
    }
    public static void main(String[] args) {
        byte[] rightNone=FreeClipProtocol.hex("5A002B002B170101000201FF0310000102030405060708090A0E0F1114FF0401FF0501FF060200FF0901000A0100C76F");
        byte[] bothWake=FreeClipProtocol.hex("5A002B002B170101000201000310000102030405060708090A0E0F1114FF0401FF0501FF060200FF0901000A01000D5C");
        byte[] leftNone=FreeClipProtocol.hex("5A002B002B170101FF0201000310000102030405060708090A0E0F1114FF0401FF0501FF060200FF0901000A0100BCAC");
        AiKeyProbeCodec.State r=AiKeyProbeCodec.parseState(rightNone);
        AiKeyProbeCodec.State b=AiKeyProbeCodec.parseState(bothWake);
        AiKeyProbeCodec.State l=AiKeyProbeCodec.parseState(leftNone);
        check(r!=null&&b!=null&&l!=null,"three user A/B captures pass CRC and parse");
        check(AiKeyActionCodec.readable(r)&&AiKeyActionCodec.readable(b)&&AiKeyActionCodec.readable(l),"two observed AI actions are readable");
        check(AiKeyActionCodec.value(r,AiKeyActionCodec.LEFT)==0&&AiKeyActionCodec.value(r,AiKeyActionCodec.RIGHT)==0xFF,"right side is TLV02");
        check(AiKeyActionCodec.value(l,AiKeyActionCodec.LEFT)==0xFF&&AiKeyActionCodec.value(l,AiKeyActionCodec.RIGHT)==0,"left side is TLV01");
        check(AiKeyActionCodec.verified(r,b,AiKeyActionCodec.RIGHT,0),"right-only wake verified");
        check(AiKeyActionCodec.verified(b,l,AiKeyActionCodec.LEFT,0xFF),"left-only none verified");
        check(!AiKeyActionCodec.verified(r,l,AiKeyActionCodec.LEFT,0xFF),"other ear changed so not verified");
        check(AiKeyActionCodec.sameSnapshot(b,b),"exact snapshot matches");
        check(!AiKeyActionCodec.sameSnapshot(r,b),"stale snapshot is rejected");
        check("唤醒智能体".equals(AiKeyActionCodec.actionName(0)),"0x00 wake agent");
        check("无".equals(AiKeyActionCodec.actionName(0xFF)),"0xFF none");
        check(AiKeyActionCodec.actionName(3).contains("0x03"),"unknown codes stay unknown");
        byte[] leftPacket=AiKeyActionCodec.verifiedWrite(1,0xFF);
        byte[] rightPacket=AiKeyActionCodec.verifiedWrite(2,0);
        check(leftPacket.length==11 && rightPacket.length==11,"experimental packet sizing");
        check(Arrays.equals(Arrays.copyOf(leftPacket,9), FreeClipProtocol.hex("5A0006002B160101FF")),"left command header and side value");
        check(Arrays.equals(Arrays.copyOf(rightPacket,9), FreeClipProtocol.hex("5A0006002B16020100")),"right command header and side value");
        check(FreeClipProtocol.crc16(leftPacket,leftPacket.length-2)==(((leftPacket[9]&255)<<8)|(leftPacket[10]&255)),"left CRC verified");
        check(FreeClipProtocol.crc16(rightPacket,rightPacket.length-2)==(((rightPacket[9]&255)<<8)|(rightPacket[10]&255)),"right CRC verified");
        check(AiKeyActionCodec.verifiedWrite(0,0)==null,"invalid side denied");
        check(AiKeyActionCodec.verifiedWrite(1,0x0A)==null,"untested actions denied");
        byte[] bad=leftNone.clone();bad[bad.length-1]^=1;
        check(AiKeyProbeCodec.parseState(bad)==null,"bad CRC rejected before state mapping");
    }
}
