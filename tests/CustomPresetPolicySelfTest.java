package dev.muuko.honorfreeclip2;
import java.util.*;

public final class CustomPresetPolicySelfTest {
    static int checks=0;
    static void ok(boolean condition,String reason){ checks++;if(!condition)throw new AssertionError(reason); }
    static List<Integer> values(int seed){
        List<Integer> l=new ArrayList<>();for(int i=0;i<10;i++)l.add(seed+i);
        return l;
    }
    static EqualizerState eq(int selected,boolean listKnown,EqualizerState.Preset... items){
        List<EqualizerState.Preset> l=Arrays.asList(items);
        EqualizerState.Preset current=null;
        for(EqualizerState.Preset x:l)if(x.id==selected)current=x;
        return new EqualizerState(true,selected,10,Arrays.asList(1,3,9),
                current==null?"默认":current.name,
                current==null?values(0):current.gains,l,listKnown);
    }
    public static void main(String[] args){
        EqualizerState.Preset a=new EqualizerState.Preset(100,"八音盒",values(-3));
        EqualizerState.Preset b=new EqualizerState.Preset(102,"古典",values(10));
        EqualizerState base=eq(100,true,a,b);
        int[] newCurve=EqSavePolicy.toArray(values(-2));
        ok(CustomPresetPolicy.validCatalog(base),"known two-slot report accepted");
        ok(CustomPresetPolicy.firstEmpty(base)==101,"middle slot free");
        ok(CustomPresetPolicy.createAllowed(base,101,"轻音乐",newCurve),"create only free slot");
        ok(!CustomPresetPolicy.createAllowed(base,100,"不可覆盖",newCurve),"cannot create over occupied slot");
        ok(!CustomPresetPolicy.createAllowed(base,101,"古典",newCurve),"duplicate name denied");
        ok(!CustomPresetPolicy.createAllowed(base,103,"越界",newCurve),"out of range denied");
        ok(!CustomPresetPolicy.createAllowed(eq(100,false,a,b),101,"轻音乐",newCurve),"missing TLV08 denies creation");
        EqualizerState.Preset c=new EqualizerState.Preset(101,"轻音乐",values(-2));
        EqualizerState created=eq(101,true,a,b,c);
        ok(CustomPresetPolicy.created(base,created,101,"轻音乐",newCurve),"creation requires exact selected ID and list");
        ok(!CustomPresetPolicy.created(base,eq(100,true,a,b,c),101,"轻音乐",newCurve),"creation not confirmed if not selected");
        ok(!CustomPresetPolicy.created(base,eq(101,true,a,b),101,"轻音乐",newCurve),"creation not confirmed if not in list");
        ok(!CustomPresetPolicy.created(base,eq(101,false,a,b,c),101,"轻音乐",newCurve),"unknown list cannot confirm creation");
        ok(!CustomPresetPolicy.createAllowed(created,101,"重复",newCurve),"cannot recreate existing");
        ok(CustomPresetPolicy.firstEmpty(created)==-1,"full preset list");
        ok(CustomPresetPolicy.switchAllowed(base,102),"select existing other slot");
        ok(!CustomPresetPolicy.switchAllowed(base,100),"cannot reselect same");
        ok(!CustomPresetPolicy.switchAllowed(base,101),"can't select uncreated slot");
        EqualizerState switched=eq(102,true,a,b);
        ok(CustomPresetPolicy.switched(base,switched,102),"switch verified only on exact preset list");
        EqualizerState.Preset corrupted=new EqualizerState.Preset(102,"古典",values(11));
        ok(!CustomPresetPolicy.switched(base,eq(102,true,a,corrupted),102),"switch cannot silently change gains");
        ok(CustomPresetPolicy.deleteAllowed(base,102),"delete non-selected slot");
        ok(!CustomPresetPolicy.deleteAllowed(base,100),"cannot delete selected");
        ok(CustomPresetPolicy.deleted(base,eq(100,true,a),102),"delete must remove target and preserve others");
        ok(!CustomPresetPolicy.deleted(base,eq(100,true,a,b),102),"delete unchanged list denied");
        ok(!CustomPresetPolicy.deleted(base,eq(100,true,corrupted),102),"delete cannot modify another slot");
        ok(CustomPresetPolicy.sameCatalog(base,eq(100,true,a,b)),"fresh equal snapshot accepted");
        ok(!CustomPresetPolicy.sameCatalog(base,eq(102,true,a,b)),"selection change means race");
        ok(!CustomPresetPolicy.sameCatalog(base,eq(100,true,a,b,c)),"other slot appeared means race");
        ok(!CustomPresetPolicy.sameCatalog(base,eq(100,false,a,b)),"missing list unknown");
        byte[] createPacket=DirectEqualizerCodec.encode(101,"轻音乐",newCurve);
        byte[] deletePacket=DirectEqualizerCodec.encodeDelete(102,"古典",EqSavePolicy.toArray(values(10)));
        ok(createPacket!=null&&deletePacket!=null,"new and delete packets encoded");
        ok(hasSequence(createPacket,new int[]{5,1,1}),"create opcode one");
        ok(hasSequence(deletePacket,new int[]{5,1,2}),"delete opcode two");
        ok(validCrc(deletePacket),"delete CRC16 matches");
        ok(validCrc(createPacket),"create CRC16 matches");
        System.out.println("CustomPresetPolicySelfTest passed: "+checks);
    }
    static boolean hasSequence(byte[] bytes,int[] seq){
        for(int i=0;i<=bytes.length-seq.length;i++){
            boolean yes=true;for(int j=0;j<seq.length;j++)if((bytes[i+j]&255)!=seq[j])yes=false;
            if(yes)return true;
        }
        return false;
    }
    static boolean validCrc(byte[] bytes){
        int n=bytes.length,c=FreeClipProtocol.crc16(bytes,n-2);
        return (bytes[n-2]&255)==(c>>>8)&&(bytes[n-1]&255)==(c&255);
    }
}
