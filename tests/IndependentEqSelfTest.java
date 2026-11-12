package dev.muuko.honorfreeclip2;

import java.util.Arrays;
import java.util.Collections;

public final class IndependentEqSelfTest {
    private static void check(boolean yes, String message) {
        if(!yes) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int[] gains={-6,-3,0,2,4,7,10,0,1,-1};
        byte[] packet=DirectEqualizerCodec.encode(0x64,"八音盒",gains);
        check(packet!=null,"valid encode");
        check((packet[0]&255)==0x5a && (packet[4]&255)==0x2b && (packet[5]&255)==0x49,"prefix");
        check(packet.length == 5 + (packet[2]&255)+((packet[3]&255)<<8),"5A frame length");
        int crc=FreeClipProtocol.crc16(packet,packet.length-2);
        check((packet[packet.length-2]&255)==(crc>>8) &&
            (packet[packet.length-1]&255)==(crc&255),"CRC");
        check((packet[8]&255)==100 && (packet[11]&255)==10 &&
              (packet[14]&255)==1 && (packet[15]&255)==3,"TLV order");
        check((packet[17]&255)==250 && (packet[18]&255)==253,"signed tenth-dB encoding");
        check(DirectEqualizerCodec.encode(99,"八音盒",gains)==null,"reject unknown ID");
        check(DirectEqualizerCodec.encode(100,"",gains)==null,"reject blank title");
        EqualizerState state=new EqualizerState(true,100,10,Collections.emptyList(),
            "八音盒",Arrays.asList(-6,-3,0,2,4,7,10,0,1,-1),
            Collections.singletonList(new EqualizerState.Preset(100,"八音盒",
                Arrays.asList(-6,-3,0,2,4,7,10,0,1,-1))));
        check(DirectEqualizerCodec.isExactReport(state,100,"八音盒",gains),"exact report");
        check(DirectEqualizerCodec.sameSnapshot(state,state),"same snapshot");
        int[] next=gains.clone();next[0]++;
        check(EqSavePolicy.trialAllowed(state,100,"八音盒",next),"single 0.1dB step");
        next[1]++;
        check(!EqSavePolicy.trialAllowed(state,100,"八音盒",next),"reject two bands");
        next=gains.clone();next[0]+=11;
        check(!EqSavePolicy.trialAllowed(state,100,"八音盒",next),"reject >1dB");
        next=gains.clone();next[0]++;
        check(!EqSavePolicy.trialAllowed(state,101,"八音盒",next),"reject other ID");
        check(!EqSavePolicy.trialAllowed(state,100,"新名字",next),"reject rename");
        check(!EqSavePolicy.trialAllowed(state,100,"八音盒",gains),"reject zero diff");
        System.out.println("IndependentEqSelfTest passed");
    }
}
