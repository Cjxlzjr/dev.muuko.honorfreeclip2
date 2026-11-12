package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** In-page EQ editor. Updates are transactional: fresh preflight + exact readback. */
final class EqualizerEditorDialog {
    private EqualizerEditorDialog() { }

    static void show(Activity a, ControlCoordinator session) {
        showInternal(a,session,-1);
    }

    static void showForCreate(Activity a,ControlCoordinator session,int slotId){
        showInternal(a,session,slotId);
    }

    private static void showInternal(Activity a,ControlCoordinator session,int newSlot){
        EqualizerState eq = session.getEqState();
        final boolean creating = newSlot >= CustomPresetPolicy.FIRST && newSlot <= CustomPresetPolicy.LAST;
        if (creating && (!CustomPresetPolicy.validCatalog(eq)
                || CustomPresetPolicy.lookup(eq,newSlot)!=null)) {
            SettingsSheet.showInfo(a,"自定义音效","槽位已占用或耳机尚未报告完整预设列表，禁止创建。");
            return;
        }
        if (a.isFinishing() || a.isDestroyed()) return;
        try {
            Dialog dialog = new Dialog(a);
            // MagicOS inherits the vendor Dialog title-divider (cyan line).
            // A transparent window background alone does not remove title decor.
            // Request NO_TITLE before setContentView to eliminate the system line.
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout sheet = SmartAudioStyle.vertical(a);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(246, 247, 250));
            float radius = SmartAudioStyle.dp(a, 31);
            bg.setCornerRadii(new float[]{radius,radius,radius,radius,0,0,0,0});
            sheet.setBackground(bg);
            sheet.setPadding(d(a, 14), d(a, 10), d(a, 14), d(a, 18));

            // This dialog is scoped to the already opened earphone page.
            View handle = new View(a);
            handle.setBackground(SmartAudioStyle.rounded(a, Color.rgb(192,194,200), 4));
            LinearLayout handleRow = SmartAudioStyle.horizontal(a);
            handleRow.setGravity(Gravity.CENTER);
            handleRow.addView(handle, new LinearLayout.LayoutParams(d(a, 42), d(a, 5)));
            sheet.addView(handleRow, new LinearLayout.LayoutParams(-1, d(a, 25)));

            LinearLayout top = SmartAudioStyle.horizontal(a);
            TextView title = SmartAudioStyle.text(a, creating ? "新建自定义音效" : "自定义音效", 22, SmartAudioStyle.TEXT, true);
            top.addView(title, new LinearLayout.LayoutParams(0, d(a, 54), 1f));
            TextView close = SmartAudioStyle.text(a, "×", 29, SmartAudioStyle.SECONDARY, false);
            close.setGravity(Gravity.CENTER);
            close.setBackground(SmartAudioStyle.rounded(a, Color.rgb(230,232,237), 24));
            top.addView(close, new LinearLayout.LayoutParams(d(a, 39), d(a, 39)));
            close.setContentDescription("关闭自定义均衡器");
            close.setOnClickListener(v -> dialog.dismiss());
            sheet.addView(top);

            ScrollView scroll = new ScrollView(a);
            scroll.setFillViewport(false);
            LinearLayout content = SmartAudioStyle.vertical(a);
            content.setPadding(d(a, 0), d(a, 4), d(a, 0), 0);
            scroll.addView(content, new android.widget.FrameLayout.LayoutParams(-1, -2));
            sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

            LinearLayout nameCard = SmartAudioStyle.card(a, content);
            LinearLayout nameRow = SmartAudioStyle.horizontal(a);
            TextView nameLabel = SmartAudioStyle.text(a, "音效名称", 16, SmartAudioStyle.TEXT, true);
            nameRow.addView(nameLabel, new LinearLayout.LayoutParams(0, d(a, 38), 1f));
            TextView name = SmartAudioStyle.text(a,
                    creating ? "我的音效 " + (newSlot - 99)
                            : eq != null && eq.selectedName != null && !eq.selectedName.isEmpty()
                            ? eq.selectedName : "本地预览", 14, SmartAudioStyle.SECONDARY, false);
            nameRow.addView(name);
            TextView arrow = SmartAudioStyle.text(a, "  ›", 20, SmartAudioStyle.SUBTLE, false);
            nameRow.addView(arrow);
            nameCard.addView(nameRow);

            LinearLayout slidersCard = SmartAudioStyle.card(a, content);
            LinearLayout toolbar = SmartAudioStyle.horizontal(a);
            TextView selectedBand = SmartAudioStyle.text(a, "选择一个频段进行调节", 12,
                    SmartAudioStyle.SECONDARY, false);
            toolbar.addView(selectedBand, new LinearLayout.LayoutParams(0, d(a, 42), 1f));
            TextView reset = SmartAudioStyle.text(a, "⟳ 重置", 14, SmartAudioStyle.SECONDARY, false);
            reset.setPadding(d(a, 5), 0, d(a, 5), 0);
            toolbar.addView(reset);
            slidersCard.addView(toolbar);

            final int[] draft = new int[EqualizerState.BANDS];
            final int[] targetPresetId = {creating ? newSlot : EqSavePolicy.targetId(eq)};
            fillDraft(draft, eq == null ? null : eq.selectedGains);
            LinearLayout graph = SmartAudioStyle.horizontal(a);
            graph.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout axis = SmartAudioStyle.vertical(a);
            TextView positive = scale(a, "+6");
            TextView zero = scale(a, "0");
            TextView negative = scale(a, "-6");
            axis.addView(positive); axis.addView(new View(a),new LinearLayout.LayoutParams(1,0,1f));
            axis.addView(zero); axis.addView(new View(a),new LinearLayout.LayoutParams(1,0,1f));
            axis.addView(negative);
            graph.addView(axis,new LinearLayout.LayoutParams(d(a, 23),d(a, 230)));
            LinearLayout bars = SmartAudioStyle.horizontal(a);
            final GainBar[] gainBars = new GainBar[EqualizerState.BANDS];
            for (int i=0; i<EqualizerState.BANDS; i++) {
                final int index = i;
                LinearLayout lane = SmartAudioStyle.vertical(a);
                GainBar bar = new GainBar(a, draft[i]);
                bar.setOnGainChanged(value -> {
                    draft[index] = value;
                    selectedBand.setText(freqLabel(index) + ": " + db(value));
                });
                bar.setContentDescription(freqLabel(i) + "，" + db(draft[i]));
                lane.addView(bar,new LinearLayout.LayoutParams(-1,d(a, 230)));
                TextView freq = scale(a, freqLabel(i));
                freq.setTextSize(9);
                freq.setGravity(Gravity.CENTER);
                lane.addView(freq,new LinearLayout.LayoutParams(-1,d(a, 32)));
                bars.addView(lane,new LinearLayout.LayoutParams(0,-2,1f));
                gainBars[i] = bar;
            }
            graph.addView(bars,new LinearLayout.LayoutParams(0,-2,1f));
            slidersCard.addView(graph,new LinearLayout.LayoutParams(-1,-2));
            reset.setOnClickListener(v -> {
                for (int i=0; i<draft.length; i++) {
                    draft[i]=0;
                    gainBars[i].setGain(0);
                }
                selectedBand.setText("全部归零（尚未写入耳机）");
            });

            TextView note = SmartAudioStyle.text(a,
                    (creating ? "新建耳机自定义预设：使用空槽位，曲线默认继承当前 10 段（如不可用则为平直）。" :
                     "编辑当前自定义预设的全部十段曲线及名称。")
                    + " 增益范围 ±6 dB，精度 0.1 dB。写入前后均进行耳机回读。", 12,
                    SmartAudioStyle.SECONDARY, false);
            note.setPadding(d(a, 12),d(a, 5),d(a, 12),d(a, 14));
            content.addView(note);

            TextView eqStatus = SmartAudioStyle.text(a, eqDescription(eq), 12,
                    SmartAudioStyle.SECONDARY, false);
            eqStatus.setPadding(d(a, 11),0,d(a, 9),d(a, 9));
            content.addView(eqStatus);

            nameRow.setOnClickListener(v -> {
                if (creating) {
                    EditText input = new EditText(a);
                    input.setSingleLine(true);
                    input.setText(name.getText().toString());
                    new AlertDialog.Builder(a).setTitle("新预设名称（最多 24 字节）")
                            .setView(input)
                            .setPositiveButton("确定",(renameDialog,button)->{
                                String text=input.getText().toString().trim();
                                if(EqSavePolicy.validName(text))name.setText(text);
                                else eqStatus.setText("名称需为 1～24 字节，中文最多约 8 字");
                            }).setNegativeButton("取消",null).show();
                    return;
                }
                List<EqualizerState.Preset> presets = eq == null ? Collections.emptyList() : eq.customPresets;
                String[] entries = new String[presets.size() + 1];
                entries[0] = "修改当前预设名称";
                for (int i=0; i<presets.size(); i++) {
                    String remote = presets.get(i).name;
                    entries[i+1] = "载入耳机预设：" +
                            (remote.isEmpty() ? "自定义音效 " + (i+1) : remote);
                }
                new AlertDialog.Builder(a).setTitle("音效名称与已保存预设")
                        .setItems(entries,(whichDialog, which) -> {
                            if (which == 0) {
                                EditText input = new EditText(a);
                                input.setSingleLine(true);
                                input.setText(name.getText().toString());
                                new AlertDialog.Builder(a).setTitle("修改预设名称（保存时生效）")
                                        .setView(input)
                                        .setPositiveButton("确定",(renameDialog,button) -> {
                                            String text = input.getText().toString().trim();
                                            if (!text.isEmpty()) name.setText(text);
                                        }).setNegativeButton("取消",null).show();
                                return;
                            }
                            EqualizerState.Preset remote = presets.get(which-1);
                            name.setText(remote.name.isEmpty() ?
                                    "自定义音效 " + which : remote.name);
                            targetPresetId[0] = remote.id;
                            fillDraft(draft,remote.gains);
                            for (int i=0;i<gainBars.length;i++) gainBars[i].setGain(draft[i]);
                            selectedBand.setText("已载入其他预设作为预览；仅当前选中槽位可以写入");
                        }).setNegativeButton("取消",null).show();
            });

            final boolean editable = creating ? CustomPresetPolicy.validCatalog(eq)
                    && CustomPresetPolicy.lookup(eq,newSlot)==null
                    : eq != null && eq.supported && eq.isCustom() && eq.hasTenBandCurve();
            TextView save = SmartAudioStyle.text(a, editable
                            ? (creating ? "新建并保存到 FreeClip 2" : "保存到 FreeClip 2")
                            : "保存（请先同步耳机 EQ 状态）", 15,
                    editable ? Color.WHITE : Color.rgb(185,199,224), true);
            save.setGravity(Gravity.CENTER);
            save.setBackground(SmartAudioStyle.rounded(a,
                    editable ? SmartAudioStyle.BLUE : Color.rgb(177,202,247),27));
            save.setEnabled(editable);
            save.setContentDescription(editable
                    ? "通过独立蓝牙通道保存全部 EQ 数据并等待耳机真实回读" : "保存不可用：尚未返回完整 EQ 状态");
            save.setOnClickListener(v -> {
                int presetId=targetPresetId[0];
                String draftName=name.getText().toString().trim();
                if (!EqSavePolicy.validName(draftName)) {
                    eqStatus.setText("名称必须为 1～24 个 UTF-8 字节（最多 8 个汉字）");
                    return;
                }
                if (!EqSavePolicy.validGains(draft) || presetId<100 || presetId>102) {
                    eqStatus.setText("无法选择有效的自定义 EQ 槽位");
                    return;
                }
                if (creating ? !CustomPresetPolicy.createAllowed(eq,presetId,draftName,draft)
                        : !EqSavePolicy.fullAllowed(eq,presetId,draftName,draft)) {
                    eqStatus.setText(creating ? "未发送：新槽位已占用、名称重复或曲线无效。"
                            : "未发送：只允许修改当前选中预设且需要有实际改动，名称不能重复。");
                    return;
                }
                int changed = creating ? 10 : EqSavePolicy.changedBands(eq, draft);
                boolean renamed = !creating && !draftName.equals(eq.selectedName);
                String warning = creating ?
                        "将创建新预设「"+draftName+"」，槽位 0x"+Integer.toHexString(presetId)+"。\n"
                        +"新建操作尚未在 FreeClip 2 上验证，发送后只以完整的耳机预设列表回读确认。"
                        +"若未确认，请不要重复保存。" :
                        "当前预设：" + eq.displayName() + "（ID " + presetId + "）\n"
                        + "修改频段：" + changed + "/10" + (renamed ? "，同时修改预设名称" : "") + "\n"
                        + "将覆盖当前选中的自定义预设，不影响其他预设槽位。"
                        + "本模块直接发送 2B/49 指令，不调用华为智慧音频。"
                        + "写入前会再次核对旧状态，写入后必须完整回读确认。";
                new AlertDialog.Builder(a).setTitle("确认保存到耳机")
                        .setMessage(warning).setNegativeButton("取消", null)
                        .setPositiveButton("继续", (confirmDialog,which) -> {
                            save.setEnabled(false);
                            eqStatus.setText("正在核对耳机现有曲线并保存…");
                            ControlCoordinator.EqSaveObserver done = (confirmed, details) -> {
                                if (confirmed) {
                                    eqStatus.setText("已保存："+details);
                                    dialog.dismiss();
                                } else {
                                    eqStatus.setText("未确认保存："+details);
                                    save.setEnabled(true);
                                }
                            };
                            if (creating) session.createCustomPreset(presetId,draftName,draft.clone(),done);
                            else session.saveEqualizer(presetId,draftName,draft.clone(),done);
                        }).show();
            });
            LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1,d(a, 51));
            saveLp.leftMargin=d(a, 64); saveLp.rightMargin=d(a, 64);
            saveLp.topMargin=d(a, 6);
            sheet.addView(save,saveLp);

            TextView undo = SmartAudioStyle.text(a,
                    creating ? "新建预设首次写入暂不提供一键撤销" : session.canUndoEqualizer() ? "撤销上次 EQ 保存（恢复耳机旧曲线）" : "本次会话暂无可撤销的 EQ 保存",
                    12, session.canUndoEqualizer() ? SmartAudioStyle.BLUE : SmartAudioStyle.SUBTLE,
                    false);
            undo.setGravity(Gravity.CENTER);
            undo.setPadding(d(a, 4), d(a, 10), d(a, 4), d(a, 7));
            undo.setEnabled(!creating && session.canUndoEqualizer());
            sheet.addView(undo, new LinearLayout.LayoutParams(-1, d(a, 42)));
            if (!creating) undo.setOnClickListener(v -> new AlertDialog.Builder(a)
                    .setTitle("撤销上次 EQ 保存")
                    .setMessage("仅恢复本次打开耳机详情页以来，最近一次成功保存前的预设名称和十段曲线。"
                            + "恢复前会复核当前耳机数据，若其他应用更改过 EQ 则拒绝覆盖。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("恢复", (confirm, which) -> {
                        undo.setEnabled(false);
                        eqStatus.setText("正在复核并恢复耳机旧 EQ…");
                        session.undoEqualizer((confirmed, details) -> {
                            if (confirmed) {
                                eqStatus.setText("恢复成功：" + details);
                                dialog.dismiss();
                            } else {
                                eqStatus.setText("恢复未确认：" + details);
                                undo.setEnabled(!creating && session.canUndoEqualizer());
                            }
                        });
                    }).show());

            dialog.setContentView(sheet);
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(0.32f);
                window.setGravity(Gravity.BOTTOM);
            }
            dialog.show();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.min(a.getResources().getDisplayMetrics().heightPixels - d(a, 48), d(a, 750)));
            }
        } catch (Throwable error) {
            android.util.Log.w(ModuleConstants.TAG, "EQ editor unavailable", error);
            new AlertDialog.Builder(a).setTitle("自定义均衡器")
                    .setMessage("界面初始化失败，请查看 LSPosed 日志。")
                    .setPositiveButton("确定",null).show();
        }
    }

    private static void fillDraft(int[] target,List<Integer> source) {
        for (int i=0;i<target.length;i++) target[i] =
                source != null && source.size()==target.length
                        ? Math.max(-60,Math.min(60,source.get(i))) : 0;
    }
    private static String eqDescription(EqualizerState eq) {
        if (eq == null) return "耳机未返回 EQ 详情 · 显示 0 dB 本地草稿";
        if (!eq.supported) return "已收到 EQ 报文，但耳机未声明该 EQ 功能可用";
        if (eq.hasTenBandCurve()) return "已读取耳机 10 段 EQ：" + eq.displayName();
        return "已读取选中音效：" + eq.displayName() + "；尚无完整 10 段曲线";
    }
    private static TextView scale(Activity a,String text) {
        TextView t=SmartAudioStyle.text(a,text,10,SmartAudioStyle.SECONDARY,false);
        t.setGravity(Gravity.CENTER);
        return t;
    }
    private static String freqLabel(int i) {
        int f=EqualizerState.FREQUENCIES[i];
        return f>=1000 ? (f/1000)+"K" : String.valueOf(f);
    }
    private static String db(int gain) {return String.format(Locale.ROOT,"%+.1f dB",gain/10f);}
    private static int d(Activity a,int dp) {return SmartAudioStyle.dp(a,dp);}

    private static final class GainBar extends View {
        interface GainListener { void onChanged(int gain); }
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int gain;
        private GainListener listener;
        GainBar(Context c,int initial) {super(c); gain=initial; setClickable(true);}
        void setOnGainChanged(GainListener l) {listener=l;}
        void setGain(int value) {
            gain=Math.max(-60,Math.min(60,value));
            invalidate();
            setContentDescription(db(gain));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx=getWidth()/2f;
            float top=dp(17),bottom=getHeight()-dp(18);
            float y=top+(60-gain)/120f*(bottom-top);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(4));
            paint.setColor(Color.rgb(222,224,228));
            canvas.drawLine(cx,top,cx,bottom,paint);
            paint.setColor(Color.rgb(27,104,234));
            canvas.drawLine(cx,y,cx,bottom,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx,y,dp(10),paint);
            paint.setColor(Color.rgb(212,215,221));
            paint.setStrokeWidth(dp(1));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(cx,y,dp(10),paint);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            int action=event.getActionMasked();
            if (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_MOVE) {
                if (getParent()!=null) getParent().requestDisallowInterceptTouchEvent(true);
                float top=dp(17), bottom=getHeight()-dp(18);
                float y=Math.max(top,Math.min(bottom,event.getY()));
                int next=Math.round(60-120*(y-top)/(bottom-top));
                if (next!=gain) {setGain(next);if(listener!=null)listener.onChanged(gain);}
                return true;
            }
            if (action==MotionEvent.ACTION_UP) {performClick();return true;}
            if (action==MotionEvent.ACTION_CANCEL) return true;
            return super.onTouchEvent(event);
        }
        @Override public boolean performClick() {super.performClick();return true;}
        private float dp(int size) {return getResources().getDisplayMetrics().density*size;}
    }
}
