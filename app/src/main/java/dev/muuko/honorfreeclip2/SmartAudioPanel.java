package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * FreeClip 2 controls rendered within Honor's own scrolling detail page.
 * The screenshot of Huawei SmartAudio is a layout reference, not an extracted layout or asset.
 * All interactive values are connected to ControlCoordinator's verified readback state.
 */
final class SmartAudioPanel {
    private SmartAudioPanel() { }

    /** Hook lifecycle callback. A page resume does not create another session. */
    static void onHomeResumed(View panel) {
        if (panel instanceof AutoSyncPanel) ((AutoSyncPanel) panel).resumeSync();
    }

    static void onHomePaused(View panel) {
        if (panel instanceof AutoSyncPanel) ((AutoSyncPanel) panel).suspendSync();
    }

    /**
     * Auto-read only while this exact control panel is attached to the screen.
     * No polling, no background service, and no permanent Bluetooth receiver.
     */
    private static final class AutoSyncPanel extends LinearLayout {
        private final BluetoothDevice device;
        private final ControlCoordinator session;
        private final Activity host;
        private boolean lifecycleRegistered;
        private final Application.ActivityLifecycleCallbacks lifecycle =
                new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) {
                if (activity == host) resumeSync();
            }
            @Override public void onActivityPaused(Activity activity) {
                if (activity == host) suspendSync();
            }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) {
                if (activity != host) return;
                suspendSync();
                if (lifecycleRegistered) {
                    try { host.getApplication().unregisterActivityLifecycleCallbacks(this); }
                    catch (Throwable ignored) { }
                    lifecycleRegistered = false;
                }
            }
        };
        private final Runnable initialRead;
        private boolean registered;
        private final BroadcastReceiver bluetoothEvents = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (device == null || intent == null) return;
                BluetoothDevice changed;
                try {
                    changed = Build.VERSION.SDK_INT >= 33
                            ? intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class)
                            : (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                } catch (Throwable e) { return; }
                if (changed == null) return;
                try {
                    if (!device.getAddress().equals(changed.getAddress())) return;
                } catch (SecurityException e) { return; }
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    removeCallbacks(initialRead);
                    session.onDisconnected();
                } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    session.onReconnected();
                    scheduleAutoRead(750);
                }
            }
        };

        AutoSyncPanel(Activity activity, BluetoothDevice device, ControlCoordinator session) {
            super(activity);
            setOrientation(VERTICAL);
            this.device = device;
            this.session = session;
            this.host = activity;
            this.initialRead = () -> {
                if (isAttachedToWindow()) this.session.autoRefresh();
            };
        }
        void scheduleAutoRead(long delayMs) {
            removeCallbacks(initialRead);
            if (isAttachedToWindow()) postDelayed(initialRead, delayMs);
        }
        void resumeSync() {
            if (!isAttachedToWindow()) return;
            startReceiver();
            scheduleAutoRead(260);
        }
        void suspendSync() {
            removeCallbacks(initialRead);
            stopReceiver();
            session.onPaused();
        }
        private void startReceiver() {
            if (!registered && device != null) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
                filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                try {
                    // Bluetooth stack can broadcast from a distinct system package.
                    if (Build.VERSION.SDK_INT >= 33)
                        getContext().registerReceiver(bluetoothEvents, filter, Context.RECEIVER_EXPORTED);
                    else getContext().registerReceiver(bluetoothEvents, filter);
                    registered = true;
                } catch (Throwable t) {
                    Log.w(ModuleConstants.TAG, "Bluetooth reconnect receiver unavailable", t);
                }
            }
        }
        private void stopReceiver() {
            if (registered) {
                try { getContext().unregisterReceiver(bluetoothEvents); }
                catch (Throwable t) { Log.w(ModuleConstants.TAG, "Bluetooth receiver release failed", t); }
                registered = false;
            }
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!lifecycleRegistered) {
                try {
                    host.getApplication().registerActivityLifecycleCallbacks(lifecycle);
                    lifecycleRegistered = true;
                } catch (Throwable t) {
                    Log.w(ModuleConstants.TAG, "Activity lifecycle observer unavailable", t);
                }
            }
            resumeSync();
        }
        @Override protected void onDetachedFromWindow() {
            suspendSync();
            if (lifecycleRegistered) {
                try { host.getApplication().unregisterActivityLifecycleCallbacks(lifecycle); }
                catch (Throwable t) { Log.w(ModuleConstants.TAG, "Lifecycle observer release failed", t); }
                lifecycleRegistered = false;
            }
            super.onDetachedFromWindow();
        }
    }

    static LinearLayout create(Activity a, BluetoothDevice device, boolean originalWearToggleExists, String tag) {
        ControlCoordinator session = new ControlCoordinator(a, device);
        AutoSyncPanel root = new AutoSyncPanel(a, device, session);
        root.setTag(tag);
        root.setPadding(d(a, 12), d(a, 9), d(a, 12), d(a, 27));
        makeHeader(a, root, session);

        // Huawei SmartAudio: 声音 / 空间音频 / 音效
        SmartAudioStyle.title(a, root, "声音");
        makeSpatialCard(a, root, session);
        makeSoundCard(a, root, session);

        // Huawei SmartAudio: 交互 / 手势控制 / 头部控制 / 语音控制
        SmartAudioStyle.title(a, root, "交互");
        makeInteractionCards(a, root, session);

        // Huawei SmartAudio: 佩戴
        SmartAudioStyle.title(a, root, "佩戴");
        LinearLayout wear = SmartAudioStyle.card(a, root);
        if (!originalWearToggleExists) {
            toggleRow(a, wear, session, FreeClipSetting.WEAR,
                    "智能佩戴检测", "摘下耳机暂停，戴上后继续播放", SmartAudioIcon.WEAR);
            separator(a, wear);
        } else {
            TextView notice = SmartAudioStyle.text(a,
                    "智能佩戴检测使用此页面上方的荣耀原生开关", 13,
                    SmartAudioStyle.SECONDARY, false);
            notice.setPadding(0, d(a, 4), 0, d(a, 6));
            wear.addView(notice);
        }
        toggleRow(a, wear, session, FreeClipSetting.DROP,
                "掉落提醒", "耳机意外掉落时提醒", SmartAudioIcon.WEAR);
        separator(a, wear);
        toggleRow(a, wear, session, FreeClipSetting.ADAPTIVE,
                "智感音量自适应", "根据周围环境调整音量", SmartAudioIcon.SOUND);

        // Huawei SmartAudio: 更多 (only implement controls backed by this project).
        SmartAudioStyle.title(a, root, "设备功能");
        makeMoreCards(a, root, session, device);

        TextView note = SmartAudioStyle.text(a,
                "FreeClip 2 扩展设置 · 设置状态以耳机真实回读为准。"
                        + "EQ 与左右 AI 键均支持独立读写；所有修改均需耳机状态回读确认。",
                11, SmartAudioStyle.SUBTLE, false);
        note.setPadding(d(a, 8), d(a, 8), d(a, 8), d(a, 7));
        root.addView(note);
        return root;
    }

    private static void makeHeader(Activity a, LinearLayout root, ControlCoordinator session) {
        LinearLayout row = SmartAudioStyle.horizontal(a);
        TextView title = SmartAudioStyle.text(a, "耳机控制", 16, SmartAudioStyle.TEXT, true);
        row.addView(title, new LinearLayout.LayoutParams(0, d(a, 43), 1f));
        TextView refresh = SmartAudioStyle.text(a, "↻  重新同步", 12, SmartAudioStyle.BLUE, false);
        refresh.setGravity(Gravity.CENTER);
        refresh.setPadding(d(a, 12), 0, d(a, 12), 0);
        refresh.setBackground(SmartAudioStyle.rounded(a, SmartAudioStyle.BLUE_PALE, 18));
        row.addView(refresh, new LinearLayout.LayoutParams(-2, d(a, 35)));
        root.addView(row);
        TextView status = SmartAudioStyle.text(a, "进入页面后自动同步耳机设置", 12,
                SmartAudioStyle.SECONDARY, false);
        status.setPadding(d(a, 4), d(a, 1), 0, d(a, 5));
        root.addView(status);
        session.bindStatus(status::setText);
        refresh.setOnClickListener(view -> session.refresh());
    }

    private static void makeSpatialCard(Activity a, LinearLayout root, ControlCoordinator session) {
        LinearLayout card = SmartAudioStyle.card(a, root);
        LinearLayout header = SmartAudioStyle.horizontal(a);
        TextView title = SmartAudioStyle.text(a, "空间音频", 17, SmartAudioStyle.TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, d(a, 40), 1f));
        TextView scene = SmartAudioStyle.text(a, "空间场景 · 未同步  ›", 12,
                SmartAudioStyle.SECONDARY, false);
        scene.setPadding(d(a, 5), 0, 0, 0);
        header.addView(scene);
        card.addView(header);

        LinearLayout segments = SmartAudioStyle.horizontal(a);
        segments.setPadding(d(a, 3), d(a, 3), d(a, 3), d(a, 3));
        segments.setBackground(SmartAudioStyle.rounded(a, SmartAudioStyle.SEGMENT, 21));
        LinearLayout.LayoutParams segmentsLp = new LinearLayout.LayoutParams(-1, d(a, 105));
        segmentsLp.topMargin = d(a, 5);
        card.addView(segments, segmentsLp);
        final int[] modes = {0, 2, 1};
        final String[] texts = {"关闭", "固定", "头部跟踪"};
        final LinearLayout[] cells = new LinearLayout[3];
        final SpatialModeGlyph[] icons = new SpatialModeGlyph[3];
        final TextView[] labels = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int selection = modes[i];
            LinearLayout cell = SmartAudioStyle.vertical(a);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(SmartAudioStyle.rounded(a, SmartAudioStyle.SEGMENT, 17));
            SpatialModeGlyph glyph = new SpatialModeGlyph(a, selection);
            cell.addView(glyph, new LinearLayout.LayoutParams(-1, d(a, 40)));
            TextView label = SmartAudioStyle.text(a, texts[i], 14,
                    SmartAudioStyle.SECONDARY, false);
            label.setGravity(Gravity.CENTER);
            cell.addView(label, new LinearLayout.LayoutParams(-1, d(a, 33)));
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, -1, 1f);
            segments.addView(cell, itemLp);
            cell.setOnClickListener(v -> {
                if (!session.isBusy() && session.get(FreeClipSetting.SPATIAL_MODE) != null)
                    session.change(FreeClipSetting.SPATIAL_MODE, selection);
            });
            cells[i] = cell; icons[i] = glyph; labels[i] = label;
        }
        session.bind(FreeClipSetting.SPATIAL_MODE, (actual, enabled, busy) -> {
            for (int i = 0; i < modes.length; i++) {
                boolean selected = actual != null && actual == modes[i];
                cells[i].setBackground(SmartAudioStyle.rounded(a,
                        selected ? SmartAudioStyle.BLUE_PALE : SmartAudioStyle.SEGMENT, 17));
                cells[i].setEnabled(enabled);
                cells[i].setAlpha(actual == null ? 0.56f : (busy ? 0.78f : 1f));
                icons[i].setTint(selected ? SmartAudioStyle.BLUE : SmartAudioStyle.SUBTLE);
                labels[i].setTextColor(selected ? SmartAudioStyle.BLUE : SmartAudioStyle.SECONDARY);
                labels[i].setTypeface(Typeface.DEFAULT,
                        selected ? Typeface.BOLD : Typeface.NORMAL);
            }
        });
        session.bind(FreeClipSetting.SPATIAL_SCENE, (actual, enabled, busy) -> {
            scene.setText(actual == null ? "空间场景 · 未同步  ›"
                    : FreeClipSetting.SPATIAL_SCENE.label(actual) + "  ›");
            scene.setTextColor(enabled ? SmartAudioStyle.SECONDARY : SmartAudioStyle.SUBTLE);
            scene.setEnabled(enabled);
        });
        scene.setOnClickListener(v -> choose(a, session, FreeClipSetting.SPATIAL_SCENE));
    }

    private static void makeSoundCard(Activity a, LinearLayout root, ControlCoordinator session) {
        LinearLayout sound = SmartAudioStyle.card(a, root);
        soundPresetRow(a, sound, session);
        separator(a, sound);
        LinearLayout eqRow = SmartAudioStyle.horizontal(a);
        eqRow.setMinimumHeight(d(a, 60));
        eqRow.addView(icon(a, SmartAudioIcon.SOUND), iconLp(a));
        LinearLayout texts = SmartAudioStyle.vertical(a);
        TextView label = SmartAudioStyle.text(a, "自定义均衡器", 15, SmartAudioStyle.TEXT, false);
        TextView sub = SmartAudioStyle.text(a, "等待同步 EQ 状态", 11,
                SmartAudioStyle.SECONDARY, false);
        texts.addView(label);
        sub.setPadding(0, d(a, 4), 0, 0);
        texts.addView(sub);
        eqRow.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView badge = SmartAudioStyle.text(a, "编辑  ›", 12,
                SmartAudioStyle.SECONDARY, false);
        eqRow.addView(badge);
        eqRow.setClickable(true);
        eqRow.setOnClickListener(v -> EqualizerEditorDialog.show(a, session));
        sound.addView(eqRow, new LinearLayout.LayoutParams(-1, d(a, 64)));
        separator(a, sound);
        LinearLayout manageRow = SmartAudioStyle.horizontal(a);
        manageRow.setGravity(Gravity.CENTER_VERTICAL);
        manageRow.setMinimumHeight(d(a, 58));
        manageRow.addView(icon(a,SmartAudioIcon.DEVICE), iconLp(a));
        LinearLayout manageTexts = SmartAudioStyle.vertical(a);
        manageTexts.addView(SmartAudioStyle.text(a,"我的音效管理",15,SmartAudioStyle.TEXT,false));
        TextView manageSub=SmartAudioStyle.text(a,"等待读取预设列表",11,
                SmartAudioStyle.SECONDARY,false);
        manageSub.setPadding(0,d(a,4),0,0);
        manageTexts.addView(manageSub);
        manageRow.addView(manageTexts,new LinearLayout.LayoutParams(0,-2,1f));
        manageRow.addView(SmartAudioStyle.text(a,"管理  ›",12,
                SmartAudioStyle.SECONDARY,false));
        sound.addView(manageRow,new LinearLayout.LayoutParams(-1,d(a,64)));
        manageRow.setOnClickListener(v->PresetManagerDialog.show(a,session));
        session.bindEqualizer(state -> {
            manageSub.setText(state == null ? "尚无预设回读" :
                    state.customCatalogKnown ? "耳机已保存 "+state.customPresets.size()+" / 3 组" :
                            "预设列表未返回，请刷新");
            if (state == null) {
                sub.setText("尚无有效回读 · 可打开本地预览");
            } else if (state.hasTenBandCurve() && state.supported) {
                sub.setText("已读取 10 段曲线 · " + state.displayName());
            } else {
                sub.setText("已读取音效 " + state.displayName() + " · 曲线未返回");
            }
        });
    }

    /** Display the actual active preset (including custom 0x64..0x66) and
     * present only device-advertised built-in choices. */
    private static void soundPresetRow(Activity a, LinearLayout sound, ControlCoordinator session) {
        LinearLayout row = SmartAudioStyle.horizontal(a);
        row.setMinimumHeight(d(a, 63));
        row.addView(icon(a, SmartAudioIcon.SOUND), iconLp(a));
        LinearLayout texts = SmartAudioStyle.vertical(a);
        TextView name = SmartAudioStyle.text(a, "音效", 15, SmartAudioStyle.TEXT, false);
        TextView hint = SmartAudioStyle.text(a, "当前预设由耳机 EQ 状态决定", 11,
                SmartAudioStyle.SECONDARY, false);
        hint.setPadding(0,d(a,3),0,0);
        texts.addView(name); texts.addView(hint);
        row.addView(texts,new LinearLayout.LayoutParams(0,-2,1f));
        TextView currentText = SmartAudioStyle.text(a, "未读取  ›", 12,
                SmartAudioStyle.SECONDARY, false);
        currentText.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.addView(currentText);
        sound.addView(row,new LinearLayout.LayoutParams(-1,d(a,67)));
        session.bind(FreeClipSetting.SOUND_EFFECT,(actual,enabled,busy) -> {
            EqualizerState eq = session.getEqState();
            currentText.setText(SoundPresetCatalog.currentLabel(actual,eq) + "  ›");
            boolean hasList = eq != null && !eq.builtInIds.isEmpty();
            hint.setText(eq == null ? "预设详情未返回，可重新同步" :
                    hasList ? "耳机回报 " + eq.builtInIds.size() + " 项内置预设" :
                            "当前音效已读取；使用 FreeClip 2 已知的四种内置预设");
            currentText.setTextColor(enabled ? SmartAudioStyle.SECONDARY : SmartAudioStyle.SUBTLE);
            row.setAlpha(actual == null ? .7f : 1f);
            row.setEnabled(enabled);
        });
        row.setOnClickListener(v -> {
            if (session.isBusy() || a.isFinishing() || a.isDestroyed()) return;
            EqualizerState eq = session.getEqState();
            java.util.List<SoundPresetCatalog.Entry> options = SoundPresetCatalog.writableOptions(eq);
            if (options.isEmpty()) {
                SettingsSheet.showInfo(a, "音效预设", "尚无可用于安全切换的详细耳机 EQ 状态。" +
                        "请先点击重新同步，并检查蓝牙控制通道。");
                return;
            }
            String[] labels = new String[options.size()];
            int selected = -1;
            for (int i=0;i<options.size();i++) {
                labels[i] = options.get(i).label;
                if (eq.selectedId == options.get(i).id) selected=i;
            }
            SettingsSheet.showChoices(a, "选择音效",
                    "当前：" + SoundPresetCatalog.currentLabel(eq.selectedId, eq),
                    labels, selected, which -> session.changeSoundPreset(options.get(which).id));
        });
    }

    private static void makeInteractionCards(Activity a, LinearLayout root, ControlCoordinator session) {
        LinearLayout grid = SmartAudioStyle.horizontal(a);
        grid.setGravity(Gravity.TOP);
        LinearLayout left = tile(a, SmartAudioIcon.GESTURE,
                "手势控制", "设置手势控制耳机", true);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, d(a, 156), 1f);
        leftLp.rightMargin = d(a, 9);
        grid.addView(left, leftLp);
        LinearLayout right = SmartAudioStyle.vertical(a);
        grid.addView(right, new LinearLayout.LayoutParams(0, d(a, 156), 1f));
        LinearLayout head = tile(a, SmartAudioIcon.HEAD, "头部控制", "点头与摇头", false);
        LinearLayout.LayoutParams smallLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        smallLp.bottomMargin = d(a, 9);
        right.addView(head, smallLp);
        LinearLayout voice = tile(a, SmartAudioIcon.VOICE, "AI 键", "智能体与左右触控区", false);
        right.addView(voice, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(grid);
        addGap(a, root, 9);

        // Detail cards live outside the homepage and are hosted only inside bottom sheets.
        LinearLayout sheetHost = SmartAudioStyle.vertical(a);
        LinearLayout gestureDetail = SmartAudioStyle.card(a, sheetHost);
        SmartAudioStyle.show(gestureDetail, false);
        detailCaption(a, gestureDetail, "手势设置", "支持双击、三击与滑动控制");
        choiceRow(a, gestureDetail, session, FreeClipSetting.DOUBLE_LEFT, "左耳双击", null, -1);
        separator(a, gestureDetail);
        choiceRow(a, gestureDetail, session, FreeClipSetting.DOUBLE_RIGHT, "右耳双击", null, -1);
        separator(a, gestureDetail);
        choiceRow(a, gestureDetail, session, FreeClipSetting.TRIPLE_LEFT, "左耳三击", null, -1);
        separator(a, gestureDetail);
        choiceRow(a, gestureDetail, session, FreeClipSetting.TRIPLE_RIGHT, "右耳三击", null, -1);
        separator(a, gestureDetail);
        choiceRow(a, gestureDetail, session, FreeClipSetting.SWIPE_LEFT, "左耳滑动", null, -1);
        separator(a, gestureDetail);
        choiceRow(a, gestureDetail, session, FreeClipSetting.SWIPE_RIGHT, "右耳滑动", null, -1);
        separator(a, gestureDetail);
        TextView longPress = SmartAudioStyle.text(a,
                "长按 / AI 键请点击主页「AI 键」卡片。", 12,
                SmartAudioStyle.SECONDARY, false);
        longPress.setPadding(0, d(a, 10), 0, d(a, 4));
        gestureDetail.addView(longPress);

        LinearLayout headDetail = SmartAudioStyle.card(a, sheetHost);
        SmartAudioStyle.show(headDetail, false);
        detailCaption(a, headDetail, "头部控制", "来电时点头接听、摇头拒接");
        toggleRow(a, headDetail, session, FreeClipSetting.HEAD_MOTION,
                "头部动作控制", "启用头部动作识别", -1);
        separator(a, headDetail);
        choiceRow(a, headDetail, session, FreeClipSetting.HEAD_NOD,
                "点头动作", null, -1);
        separator(a, headDetail);
        choiceRow(a, headDetail, session, FreeClipSetting.HEAD_SHAKE,
                "摇头动作", null, -1);

        LinearLayout aiDetail = SmartAudioStyle.card(a, sheetHost);
        SmartAudioStyle.show(aiDetail, false);
        detailCaption(a, aiDetail, "AI 键设置", "分别设置左右耳的触控操作");
        TextView assistantValue = readOnlyDetailRow(a, aiDetail,
                "系统语音助手设置", "前往 MagicOS 设置  ›");
        ((View) assistantValue.getParent()).setOnClickListener(v -> openAssistantSettings(a));
        TextView assistantNotice = SmartAudioStyle.text(a,
                "「唤醒智能体」将使用手机当前的语音助手。具体唤醒对象由系统决定。",
                12, SmartAudioStyle.SECONDARY, false);
        assistantNotice.setPadding(0, d(a, 7), 0, d(a, 9));
        aiDetail.addView(assistantNotice);
        separator(a, aiDetail);
        TextView leftAiValue = readOnlyDetailRow(a, aiDetail,
                "左侧触控区", "正在读取…");
        separator(a, aiDetail);
        TextView rightAiValue = readOnlyDetailRow(a, aiDetail,
                "右侧触控区", "正在读取…");
        ((View) leftAiValue.getParent()).setOnClickListener(v -> showAiActions(a, session, 1));
        ((View) rightAiValue.getParent()).setOnClickListener(v -> showAiActions(a, session, 2));

        // Keep protocol details in internal code/logging, not in the user-facing sheet.
        TextView aiStatus = SmartAudioStyle.text(a, "打开页面后会自动读取当前设置", 12,
                SmartAudioStyle.SECONDARY, false);
        aiStatus.setPadding(0, d(a, 11), 0, d(a, 8));
        aiDetail.addView(aiStatus);
        session.bindAiState(state -> {
            leftAiValue.setText(aiActionLabel(state, AiKeyActionCodec.LEFT));
            rightAiValue.setText(aiActionLabel(state, AiKeyActionCodec.RIGHT));
        });

        TextView refreshAi = SmartAudioStyle.text(a, "刷新状态  ›", 13,
                SmartAudioStyle.BLUE, true);
        refreshAi.setPadding(0, d(a, 10), 0, d(a, 9));
        aiDetail.addView(refreshAi);
        Runnable refreshAiState = () -> {
            if (session.isBusy() || a.isFinishing() || a.isDestroyed()) {
                aiStatus.setText("正在同步耳机设置，请稍后再试");
                return;
            }
            aiStatus.setText("正在读取…");
            session.probeAiKey((report, state) -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                aiStatus.setText(state == null
                        ? "读取失败，请确认耳机已连接后重试"
                        : "已同步当前设置");
            });
        };
        refreshAi.setOnClickListener(v -> refreshAiState.run());

        left.setOnClickListener(v -> SettingsSheet.show(a, "手势控制", gestureDetail));
        head.setOnClickListener(v -> SettingsSheet.show(a, "头部控制", headDetail));
        voice.setOnClickListener(v -> {
            SettingsSheet.show(a, "AI 键", aiDetail);
            // Read only when the sheet opens, without adding homepage latency.
            refreshAiState.run();
        });
    }

    /** Keep unrecognized firmware codes read-only, without exposing raw bytes in the UI. */
    private static String aiActionLabel(AiKeyProbeCodec.State state, int side) {
        if (state == null) return "未读取";
        int value = AiKeyActionCodec.value(state, side);
        if (!AiKeyActionCodec.actionAllowed(value)) return "暂不支持";
        return AiKeyActionCodec.actionName(value) + "  ›";
    }

    private static void openAssistantSettings(Activity a) {
        try {
            a.startActivity(new Intent("android.settings.VOICE_INPUT_SETTINGS"));
        } catch (Throwable missing) {
            try { a.startActivity(new Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS")); }
            catch (Throwable fail) {
                android.widget.Toast.makeText(a, "无法打开系统语音设置，请手动前往 MagicOS 设置", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }


    /** FreeClip 2 AI key write limited to verified 00/FF values. */
    private static void showAiActions(Activity a,ControlCoordinator session,int side) {
        AiKeyProbeCodec.State current=session.getAiState();
        if (!AiKeyActionCodec.readable(current) || session.isBusy()) {
            android.widget.Toast.makeText(a,"暂时无法修改，请确认耳机连接并刷新状态",android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        final int before=AiKeyActionCodec.value(current,side);
        final String target=side==AiKeyActionCodec.LEFT?"左侧触控区":"右侧触控区";
        final int[] actions={AiKeyActionCodec.WAKE_AGENT,AiKeyActionCodec.NONE};
        SettingsSheet.showChoices(a,target,"选择触控操作",
                new String[]{"唤醒智能体","无"},before==actions[0]?0:1,index -> {
                    int desired=actions[index];
                    if (desired==before) return;
                    new AlertDialog.Builder(a)
                            .setTitle("确认修改"+target)
                            .setMessage("将「" + target + "」设置为「"
                                    + AiKeyActionCodec.actionName(desired) + "」？"
                                    + "\n保存后会自动检查耳机是否已应用此设置。")
                            .setNegativeButton("取消",null)
                            .setPositiveButton("确认修改",(dlg,which) -> session.changeAiKey(side,desired,(confirmed,details) -> {
                                android.widget.Toast.makeText(a,
                                        confirmed ? "设置已保存" : "保存未成功，请刷新状态后重试",
                                        android.widget.Toast.LENGTH_LONG).show();
                            })).show();
                });
    }

    private static void makeMoreCards(Activity a, LinearLayout root, ControlCoordinator session, BluetoothDevice device) {
        LinearLayout row1 = SmartAudioStyle.horizontal(a);
        LinearLayout deviceTile = tile(a, SmartAudioIcon.DEVICE,
                "设备设置", "双设备与低时延", true);
        LinearLayout caseTile = tile(a, SmartAudioIcon.CASE,
                "充电盒设置", "提示音", true);
        addTwoTiles(a, row1, deviceTile, caseTile);
        root.addView(row1);
        addGap(a, root, 9);
        LinearLayout row2 = SmartAudioStyle.horizontal(a);
        LinearLayout labTile = tile(a, SmartAudioIcon.LAB,
                "实验室", "嘈杂环境大音量", true);
        LinearLayout updateTile = tile(a, SmartAudioIcon.UPDATE,
                "固件信息", "离线只读 · 不提供 OTA", true);
        addTwoTiles(a, row2, labTile, updateTile);
        root.addView(row2);
        addGap(a, root, 9);

        LinearLayout sheetHost = SmartAudioStyle.vertical(a);
        LinearLayout deviceDetail = SmartAudioStyle.card(a, sheetHost);
        SmartAudioStyle.show(deviceDetail, false);
        detailCaption(a, deviceDetail, "设备设置", "连接和游戏选项");
        toggleRow(a, deviceDetail, session, FreeClipSetting.DUAL_DEVICE,
                "双设备连接", "耳机侧双设备连接开关", SmartAudioIcon.DEVICE);
        separator(a, deviceDetail);
        toggleRow(a, deviceDetail, session, FreeClipSetting.LOW_LATENCY,
                "游戏低时延", "低时延功能需要耳机支持", SmartAudioIcon.LAB);
        separator(a, deviceDetail);
        TextView deviceInfo = SmartAudioStyle.text(a, "设备信息  ›", 15,
                SmartAudioStyle.TEXT, false);
        deviceInfo.setPadding(0, d(a, 13), 0, d(a, 13));
        deviceDetail.addView(deviceInfo);
        LinearLayout infoDetail = SmartAudioStyle.vertical(a);
        SmartAudioStyle.show(infoDetail, false);
        readOnlyDetailRow(a, infoDetail, "设备名称", safeDeviceName(device));
        separator(a, infoDetail);
        readOnlyDetailRow(a, infoDetail, "固件版本", "未读取 · 尚无可靠固件查询协议");
        deviceDetail.addView(infoDetail);
        deviceInfo.setOnClickListener(v -> SettingsSheet.show(a, "设备信息", infoDetail));

        LinearLayout caseDetail = SmartAudioStyle.card(a, sheetHost);
        SmartAudioStyle.show(caseDetail, false);
        detailCaption(a, caseDetail, "充电盒设置", "提示音控制");
        caseSoundRow(a, caseDetail, session);

        LinearLayout labDetail = SmartAudioStyle.card(a, sheetHost);
        SmartAudioStyle.show(labDetail, false);
        detailCaption(a, labDetail, "实验室", "实验性耳机设置");
        toggleRow(a, labDetail, session, FreeClipSetting.NOISY,
                "嘈杂环境大音量", "环境噪声较大时增强音量", SmartAudioIcon.LAB);

        deviceTile.setOnClickListener(v -> SettingsSheet.show(a, "设备设置", deviceDetail));
        caseTile.setOnClickListener(v -> SettingsSheet.show(a, "充电盒设置", caseDetail));
        labTile.setOnClickListener(v -> SettingsSheet.show(a, "实验室", labDetail));
        updateTile.setOnClickListener(v -> info(a, "固件信息",
                "独立版暂未获取固件版本。为降低升级风险，模块不执行 OTA 写入。"));
    }

    /** Read-only AI key rows do not accidentally persist a value from a screenshot. */
    private static TextView readOnlyDetailRow(Activity a, LinearLayout card, String title, String summary) {
        LinearLayout row = SmartAudioStyle.horizontal(a);
        row.setMinimumHeight(d(a, 54));
        TextView name = SmartAudioStyle.text(a, title, 15, SmartAudioStyle.TEXT, false);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = SmartAudioStyle.text(a, summary, 12, SmartAudioStyle.SECONDARY, false);
        row.addView(value);
        card.addView(row);
        return value;
    }

    /**
     * The case protocol is only available when both earbuds are in the case
     * and its lid is open. The app cannot verify that physical condition from
     * a generic Bluetooth ACL event; require explicit local user confirmation.
     */
    private static void caseSoundRow(Activity a, LinearLayout card, ControlCoordinator session) {
        LinearLayout row = SmartAudioStyle.horizontal(a);
        row.setMinimumHeight(d(a, 58));
        row.addView(icon(a, SmartAudioIcon.CASE), iconLp(a));
        LinearLayout texts = SmartAudioStyle.vertical(a);
        texts.addView(SmartAudioStyle.text(a, "充电盒提示音", 15, SmartAudioStyle.TEXT, false));
        TextView sub = SmartAudioStyle.text(a, "仅双耳均入盒且开盖时可设置", 11,
                SmartAudioStyle.SECONDARY, false);
        sub.setPadding(0, d(a, 4), 0, 0);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = SmartAudioStyle.toggle(a);
        row.addView(sw);
        card.addView(row);
        CheckBox checked = new CheckBox(a);
        checked.setText("我已确认：双耳均放入充电盒且保持开盖");
        checked.setTextSize(12);
        checked.setTextColor(SmartAudioStyle.SECONDARY);
        card.addView(checked);
        TextView warning = SmartAudioStyle.text(a,
                "此勾选仅用于解锁操作，不代表已检测到充电盒状态。"
                        + "如果耳机已断开或未返回提示音状态，开关仍不可用。", 11,
                SmartAudioStyle.SECONDARY, false);
        warning.setPadding(0, d(a, 2), 0, d(a, 7));
        card.addView(warning);
        final boolean[] binding = {false};
        Runnable update = () -> {
            Integer actual = session.get(FreeClipSetting.CASE_SOUND);
            binding[0] = true;
            sw.setChecked(actual != null && actual == 1);
            binding[0] = false;
            sw.setEnabled(checked.isChecked() && actual != null && !session.isBusy());
            sub.setText(actual == null ? "未读取到充电盒提示音状态"
                    : (actual == 1 ? "当前：已开启" : "当前：已关闭"));
        };
        checked.setOnCheckedChangeListener((button, isChecked) -> {
            // Confirming physical conditions requires a fresh check of earbud state.
            if (isChecked && !session.isBusy()) session.refresh();
            update.run();
            if (isChecked) checked.postDelayed(() -> checked.setChecked(false), 60_000L);
        });
        sw.setOnCheckedChangeListener((button, isChecked) -> {
            if (binding[0]) return;
            if (checked.isChecked() && session.get(FreeClipSetting.CASE_SOUND) != null
                    && !session.isBusy()) {
                session.change(FreeClipSetting.CASE_SOUND, isChecked ? 1 : 0);
                checked.setChecked(false); // one explicit confirmation per write
            }
        });
        session.bind(FreeClipSetting.CASE_SOUND, (actual, enabled, busy) -> update.run());
    }

    private static String safeDeviceName(BluetoothDevice device) {
        if (device == null) return "当前设备未知";
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? "HUAWEI FreeClip 2" : name;
        } catch (SecurityException e) { return "未授权读取蓝牙名称"; }
    }

    private static LinearLayout tile(Activity a, int glyph, String title, String subtitle, boolean tall) {
        LinearLayout tile = SmartAudioStyle.vertical(a);
        tile.setBackground(SmartAudioStyle.rounded(a, SmartAudioStyle.SURFACE, 20));
        tile.setPadding(d(a, 13), d(a, tall ? 14 : 10), d(a, 10), d(a, 9));
        tile.setGravity(tall ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        SmartAudioIcon icon = icon(a, glyph);
        tile.addView(icon, iconLp(a));
        if (tall) tile.addView(new View(a), new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView name = SmartAudioStyle.text(a, title, 15, SmartAudioStyle.TEXT, true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = d(a, tall ? 3 : 1);
        tile.addView(name, np);
        if (tall) {
            TextView caption = SmartAudioStyle.text(a, subtitle, 11,
                    SmartAudioStyle.SECONDARY, false);
            caption.setPadding(0, d(a, 4), 0, 0);
            tile.addView(caption);
        }
        tile.setClickable(true);
        return tile;
    }

    private static void addTwoTiles(Activity a, LinearLayout parent,
                                     LinearLayout left, LinearLayout right) {
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, d(a, 118), 1f);
        first.rightMargin = d(a, 9);
        parent.addView(left, first);
        parent.addView(right, new LinearLayout.LayoutParams(0, d(a, 118), 1f));
    }

    private static void detailCaption(Activity a, LinearLayout c,
                                      String title, String subtitle) {
        // The sheet header already shows the title; avoid repeating it inside the card.
        TextView hint = SmartAudioStyle.text(a, subtitle, 12, SmartAudioStyle.SECONDARY, false);
        hint.setPadding(0, d(a, 1), 0, d(a, 14));
        c.addView(hint);
    }

    private static void toggleRow(Activity a, LinearLayout card, ControlCoordinator session,
                                  FreeClipSetting setting, String title, String subtitle, int glyph) {
        LinearLayout row = SmartAudioStyle.horizontal(a);
        row.setMinimumHeight(d(a, 56));
        if (glyph >= 0) row.addView(icon(a, glyph), iconLp(a));
        LinearLayout texts = SmartAudioStyle.vertical(a);
        TextView name = SmartAudioStyle.text(a, title, 15, SmartAudioStyle.TEXT, false);
        TextView hint = SmartAudioStyle.text(a, subtitle, 11, SmartAudioStyle.SECONDARY, false);
        hint.setPadding(0, d(a, 3), d(a, 7), 0);
        texts.addView(name); texts.addView(hint);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = SmartAudioStyle.toggle(a);
        row.addView(toggle);
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        final boolean[] fromReadback = {false};
        toggle.setOnCheckedChangeListener((CompoundButton button, boolean value) -> {
            if (!fromReadback[0]) session.change(setting, value ? 1 : 0);
        });
        session.bind(setting, (actual, enabled, busy) -> {
            fromReadback[0] = true;
            toggle.setChecked(actual != null && actual == 1);
            fromReadback[0] = false;
            toggle.setEnabled(enabled);
            name.setAlpha(enabled || busy ? 1f : .65f);
            hint.setText(actual == null ? "等待状态同步 · " + subtitle : subtitle);
        });
    }

    private static void choiceRow(Activity a, LinearLayout card, ControlCoordinator session,
                                  FreeClipSetting setting, String title, String subtitle, int glyph) {
        LinearLayout row = SmartAudioStyle.horizontal(a);
        row.setMinimumHeight(d(a, 53));
        if (glyph >= 0) row.addView(icon(a, glyph), iconLp(a));
        LinearLayout texts = SmartAudioStyle.vertical(a);
        TextView name = SmartAudioStyle.text(a, title, 15, SmartAudioStyle.TEXT, false);
        texts.addView(name);
        if (subtitle != null) {
            TextView desc = SmartAudioStyle.text(a, subtitle, 11,
                    SmartAudioStyle.SECONDARY, false);
            desc.setPadding(0, d(a, 3), 0, 0);
            texts.addView(desc);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = SmartAudioStyle.text(a, "未读取  ›", 12,
                SmartAudioStyle.SUBTLE, false);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(value);
        card.addView(row, new LinearLayout.LayoutParams(-1, d(a, subtitle == null ? 50 : 61)));
        session.bind(setting, (actual, enabled, busy) -> {
            value.setText(setting.label(actual) + "  ›");
            value.setTextColor(enabled ? SmartAudioStyle.SECONDARY : SmartAudioStyle.SUBTLE);
            row.setAlpha(actual == null ? .64f : 1f);
            row.setEnabled(enabled);
        });
        row.setOnClickListener(v -> choose(a, session, setting));
    }

    private static void choose(Activity a, ControlCoordinator session, FreeClipSetting setting) {
        if (a.isFinishing() || a.isDestroyed() || session.isBusy()) return;
        Integer current = session.get(setting);
        if (current == null || !setting.supported) return;
        int index = -1;
        for (int i = 0; i < setting.values.length; i++) {
            if (setting.values[i] == current) { index = i; break; }
        }
        try {
            SettingsSheet.showChoices(a, setting.title, "当前：" + setting.label(current),
                    setting.labels, index, which -> session.change(setting, setting.values[which]));
        } catch (Throwable error) {
            Log.w(ModuleConstants.TAG, "SmartAudio bottom sheet choice failed: " + setting, error);
        }
    }

    private static void info(Activity a, String title, String message) {
        try {
            SettingsSheet.showInfo(a, title, message);
        } catch (Throwable t) {
            Log.w(ModuleConstants.TAG, "SmartAudio bottom sheet info failed", t);
        }
    }

    private static void addGap(Activity a, LinearLayout target, int dp) {
        View space = new View(a);
        target.addView(space, new LinearLayout.LayoutParams(-1, d(a, dp)));
    }

    private static void separator(Activity a, LinearLayout card) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, d(a, 1));
        p.topMargin = d(a, 6); p.bottomMargin = d(a, 6);
        card.addView(SmartAudioStyle.divider(a), p);
    }

    private static SmartAudioIcon icon(Activity a, int kind) {
        return new SmartAudioIcon(a, kind);
    }

    private static LinearLayout.LayoutParams iconLp(Activity a) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(d(a, 31), d(a, 31));
        p.rightMargin = d(a, 11);
        return p;
    }

    private static int d(Activity a, float px) { return SmartAudioStyle.dp(a, px); }
}
