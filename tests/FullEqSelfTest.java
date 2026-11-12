package dev.muuko.honorfreeclip2;
import java.util.*;

/** EQ multi-band, rename, rollback and preflight policy regression tests. */
public final class FullEqSelfTest {
    private static int count;
    private static void ok(boolean cond, String reason) {
        count++;
        if (!cond) throw new AssertionError(reason);
    }
    private static EqualizerState original() {
        List<Integer> curve = Arrays.asList(2,-4,0,9,-2,1,5,12,0,-8);
        List<EqualizerState.Preset> saved = Arrays.asList(
            new EqualizerState.Preset(100,"八音盒",curve),
            new EqualizerState.Preset(101,"古典",Arrays.asList(0,0,0,0,0,0,0,0,0,0))
        );
        return new EqualizerState(true,100,10,Arrays.asList(1,3,9),"八音盒",curve,saved);
    }
    public static void main(String[] args) {
        EqualizerState current = original();
        int[] multi = EqSavePolicy.toArray(current.selectedGains);
        multi[0] += 30;
        multi[6] -= 20;
        multi[9] = -60;
        ok(EqSavePolicy.targetId(current)==100,"only selected custom slot");
        ok(EqSavePolicy.changedBands(current,multi)==3,"three bands changed");
        ok(EqSavePolicy.fullAllowed(current,100,"八音盒",multi),"multiple bands allowed");
        ok(EqSavePolicy.fullAllowed(current,100,"新的音效",multi),"rename plus multiple bands allowed");
        ok(EqSavePolicy.fullAllowed(current,100,"新的音效",EqSavePolicy.toArray(current.selectedGains)),"rename only allowed");
        ok(!EqSavePolicy.fullAllowed(current,100,"八音盒",EqSavePolicy.toArray(current.selectedGains)),"no-op disallowed");
        ok(!EqSavePolicy.fullAllowed(current,101,"八音盒",multi),"other preset ID denied");
        ok(!EqSavePolicy.fullAllowed(current,100,"古典",multi),"colliding saved preset name denied");
        ok(!EqSavePolicy.validName("测试测试测试测试测"),"over 24 bytes rejected");
        ok(EqSavePolicy.validName("测试测试测试测试"),"24 bytes allowed");
        int[] excessive = multi.clone(); excessive[5] = 61;
        ok(!EqSavePolicy.fullAllowed(current,100,"八音盒",excessive),"out of range denied");
        ok(!EqSavePolicy.fullAllowed(current,100,"八音盒",new int[9]),"9 bands denied");
        ok(!EqSavePolicy.fullAllowed(null,100,"八音盒",multi),"no current readback denied");
        EqualizerState second = EqSavePolicy.afterConfirmed(current,100,"新的音效",multi);
        ok(DirectEqualizerCodec.isExactReport(second,100,"新的音效",multi),"complete exact response");
        ok(EqSavePolicy.canRestore(second,current),"undo allowed for same slot");
        int[] old = EqSavePolicy.toArray(current.selectedGains);
        ok(EqSavePolicy.fullAllowed(second,100,current.selectedName,old),"undo write allowed");
        ok(!EqSavePolicy.canRestore(current,current),"no-op undo denied");
        byte[] packet = DirectEqualizerCodec.encode(100,"新的音效",multi);
        ok(packet!=null,"full modified packet encoded");
        int crc=FreeClipProtocol.crc16(packet,packet.length-2);
        ok((packet[packet.length-2]&255)==(crc>>8) && (packet[packet.length-1]&255)==(crc&255),"frame CRC matches");
        EqualizerState altered = EqSavePolicy.afterConfirmed(current,100,"另一音效",multi);
        ok(!DirectEqualizerCodec.sameSnapshot(altered,current),"stale preflight rejected");
        ok(!EqSavePolicy.canRestore(altered,current) || !DirectEqualizerCodec.sameSnapshot(altered,second),
           "unexpected current state is not successful state of transaction");
        System.out.println("FullEqSelfTest passed: " + count);
    }
}
