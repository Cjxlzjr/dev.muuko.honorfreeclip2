package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Full custom EQ three-slot management in the same no-title bottom-sheet style. */
final class PresetManagerDialog {
    private PresetManagerDialog() { }
    private static int dp(Activity a,int value){return SmartAudioStyle.dp(a,value);}

    static void show(Activity a,ControlCoordinator session){
        if(a==null||a.isFinishing()||a.isDestroyed())return;
        EqualizerState eq=session.getEqState();
        if(!CustomPresetPolicy.validCatalog(eq)){
            SettingsSheet.showInfo(a,"自定义预设管理",
                    "耳机尚未返回带 TLV08 的完整自定义预设列表。为防止误写，不允许猜测空槽位。请先重新同步状态。");
            return;
        }
        Dialog dialog=new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=SmartAudioStyle.vertical(a);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(Color.rgb(246,247,250));
        float radius=dp(a,31);
        bg.setCornerRadii(new float[]{radius,radius,radius,radius,0,0,0,0});
        sheet.setBackground(bg);
        sheet.setPadding(dp(a,14),dp(a,10),dp(a,14),dp(a,20));

        LinearLayout handleRow=SmartAudioStyle.horizontal(a);
        handleRow.setGravity(Gravity.CENTER);
        View handle=new View(a);
        handle.setBackground(SmartAudioStyle.rounded(a,Color.rgb(192,194,200),4));
        handleRow.addView(handle,new LinearLayout.LayoutParams(dp(a,42),dp(a,5)));
        sheet.addView(handleRow,new LinearLayout.LayoutParams(-1,dp(a,24)));

        LinearLayout header=SmartAudioStyle.horizontal(a);
        TextView title=SmartAudioStyle.text(a,"我的音效",22,SmartAudioStyle.TEXT,true);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(a,58),1f));
        TextView close=SmartAudioStyle.text(a,"×",28,SmartAudioStyle.SECONDARY,false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(SmartAudioStyle.rounded(a,Color.rgb(230,232,237),24));
        close.setContentDescription("关闭自定义预设管理");
        close.setOnClickListener(v->dialog.dismiss());
        header.addView(close,new LinearLayout.LayoutParams(dp(a,39),dp(a,39)));
        sheet.addView(header);

        ScrollView scroll=new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list=SmartAudioStyle.vertical(a);
        list.setPadding(0,0,0,dp(a,12));
        scroll.addView(list);
        sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));

        TextView count=SmartAudioStyle.text(a,
                "已保存 "+eq.customPresets.size()+" / 3 组 · 预设列表来自耳机实时回读",12,
                SmartAudioStyle.SECONDARY,false);
        count.setPadding(dp(a,10),0,0,dp(a,12));
        list.addView(count);

        for(int slot=CustomPresetPolicy.FIRST;slot<=CustomPresetPolicy.LAST;slot++){
            final int id=slot;
            EqualizerState.Preset preset=CustomPresetPolicy.lookup(eq,id);
            boolean selected=eq.selectedId==id;
            LinearLayout card=SmartAudioStyle.card(a,list);
            if(preset==null){
                TextView free=SmartAudioStyle.text(a,"空槽位 "+(id-CustomPresetPolicy.FIRST+1),16,
                        SmartAudioStyle.SECONDARY,true);
                card.addView(free);
                TextView hint=SmartAudioStyle.text(a,"可新建 10 段 EQ 预设",12,
                        SmartAudioStyle.SECONDARY,false);
                hint.setPadding(0,dp(a,4),0,dp(a,4));card.addView(hint);
                TextView add=action(a,"＋ 在此槽位新建",true);
                card.addView(add);
                add.setOnClickListener(v->{
                    if(session.isBusy()){info(a,"耳机正在同步，请稍后再试");return;}
                    dialog.dismiss();
                    EqualizerEditorDialog.showForCreate(a,session,id);
                });
            } else {
                TextView name=SmartAudioStyle.text(a,preset.name+
                        (selected?"  ·  当前使用":""),16,SmartAudioStyle.TEXT,true);
                card.addView(name);
                TextView details=SmartAudioStyle.text(a,String.format(java.util.Locale.ROOT,
                        "槽位 %d · ID 0x%02X · 10 段已读取",id-99,id),12,
                        SmartAudioStyle.SECONDARY,false);
                details.setPadding(0,dp(a,4),0,dp(a,8));
                card.addView(details);
                LinearLayout actions=SmartAudioStyle.horizontal(a);
                if(selected){
                    TextView edit=action(a,"编辑 / 改名",true);
                    actions.addView(edit,new LinearLayout.LayoutParams(0,dp(a,44),1f));
                    edit.setOnClickListener(v->{dialog.dismiss();EqualizerEditorDialog.show(a,session);});
                }else{
                    TextView use=action(a,"切换到此音效",true);
                    actions.addView(use,new LinearLayout.LayoutParams(0,dp(a,44),1f));
                    use.setOnClickListener(v->{
                        if(session.isBusy()){info(a,"耳机正在同步，请稍后再试");return;}
                        new AlertDialog.Builder(a).setTitle("切换自定义音效")
                                .setMessage("切换到「"+preset.name+"」？将使用耳机已保存的原始曲线，写入前后核对全部三个槽位。")
                                .setNegativeButton("取消",null)
                                .setPositiveButton("切换",(d,w)->{
                                    dialog.dismiss();
                                    session.selectCustomPreset(id,(ok,message)->{
                                        result(a,ok,message);
                                        if(ok)show(a,session);
                                    });
                                }).show();
                    });
                    TextView delete=action(a,"删除",false);
                    LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(a,96),dp(a,44));
                    dl.leftMargin=dp(a,10);
                    actions.addView(delete,dl);
                    delete.setOnClickListener(v->{
                        if(session.isBusy()){info(a,"耳机正在同步，请稍后再试");return;}
                        new AlertDialog.Builder(a).setTitle("删除耳机中的自定义音效")
                                .setMessage("将删除「"+preset.name+"」（0x"+
                                        Integer.toHexString(id).toUpperCase(java.util.Locale.ROOT)+"）。\n"
                                        +"这一操作目前尚未在 FreeClip 2 上实机验证，无法自动撤销。"
                                        +"删除前重新检查耳机三槽位列表，并仅发送一次候选删除命令。"
                                        +"如果耳机没有确认移除，请不要连续重试。")
                                .setNegativeButton("取消",null)
                                .setPositiveButton("确认删除",(d,w)->{
                                    dialog.dismiss();
                                    session.deleteCustomPreset(id,(ok,message)->{
                                        result(a,ok,message);
                                        if(ok)show(a,session);
                                    });
                                }).show();
                    });
                }
                card.addView(actions);
            }
        }
        TextView warning=SmartAudioStyle.text(a,
                "已有预设的当前槽位编辑已在之前版本验证。新建、切换其他自定义槽位及删除采用独立 2B/49 协议，仍需首次实机验证；所有成功状态以耳机完整回读为准。",12,
                SmartAudioStyle.SECONDARY,false);
        warning.setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,12));
        list.addView(warning);

        dialog.setContentView(sheet);
        Window window=dialog.getWindow();
        if(window!=null){
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.32f);window.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
        if(window!=null)window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(a.getResources().getDisplayMetrics().heightPixels-dp(a,50),dp(a,680)));
    }
    private static TextView action(Activity a,String title,boolean primary){
        TextView v=SmartAudioStyle.text(a,title,13,
                primary?SmartAudioStyle.BLUE:Color.rgb(207,65,70),true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(SmartAudioStyle.rounded(a,
                primary?Color.rgb(235,242,254):Color.rgb(253,238,238),15));
        v.setPadding(dp(a,8),0,dp(a,8),0);return v;
    }
    private static void result(Activity a,boolean ok,String message){
        if(a.isFinishing()||a.isDestroyed())return;
        Toast.makeText(a,(ok?"耳机已确认：":"操作未确认：")+message,Toast.LENGTH_LONG).show();
    }
    private static void info(Activity a,String message){Toast.makeText(a,message,Toast.LENGTH_SHORT).show();}
}
