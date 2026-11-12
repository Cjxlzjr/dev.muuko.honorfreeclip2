package dev.muuko.honorfreeclip2;
import java.util.*;

public final class EqSavePolicySelfTest {
    private static int count;
    private static void ok(boolean b,String why){count++;if(!b)throw new AssertionError(why);}
    public static void main(String[]args){
        List<Integer> curve=new ArrayList<>();for(int i=0;i<10;i++)curve.add(i-5);
        EqualizerState initial=new EqualizerState(true,1,10,Arrays.asList(1,9),
                "默认",curve,Collections.emptyList());
        ok(EqSavePolicy.targetId(initial)==-1,"built-in cannot create new slots in v0.9");
        int[] g=new int[10]; Arrays.fill(g,10);
        ok(EqSavePolicy.validGains(g),"ten bands");
        ok(!EqSavePolicy.validGains(new int[9]),"reject nine bands");
        g[5]=61;ok(!EqSavePolicy.validGains(g),"reject out of range"); g[5]=10;
        ok(EqSavePolicy.validName("八音盒"),"chinese name");
        ok(!EqSavePolicy.validName(" "),"blank name");
        ok(!EqSavePolicy.validName("三十多个汉字不该允许重复重复重复重复重复重复"),"byte length");
        EqualizerState saved=EqSavePolicy.afterConfirmed(initial,100,"八音盒",g);
        ok(saved.selectedId==100 && saved.isCustom(),"after confirmed id");
        ok(saved.customPresets.size()==1 && "八音盒".equals(saved.selectedName),"selected name");
        ok(saved.selectedGains.get(5)==10,"new curve");
        ok(EqSavePolicy.targetId(saved)==100,"overwrite active custom");
        ok(EqSavePolicy.overwrites(saved,100),"overwrite warning");
        EqualizerState next=EqSavePolicy.afterConfirmed(saved,100,"更新",g);
        ok(next.customPresets.size()==1,"replace no duplication");
        EqualizerState full=new EqualizerState(true,1,10,Arrays.asList(1),"默认",curve,Arrays.asList(
                new EqualizerState.Preset(100,"A",curve),
                new EqualizerState.Preset(101,"B",curve),
                new EqualizerState.Preset(102,"C",curve)));
        ok(EqSavePolicy.targetId(full)==-1,"no silent overwrite built in");
        ok(EqSavePolicy.targetId(new EqualizerState(false,100,10,Collections.emptyList(),"A",curve,Collections.emptyList()))==-1,"unsupported readback");
        System.out.println("EqSavePolicySelfTest passed: "+count);
    }
}
