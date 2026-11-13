package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.Map;

/** In-memory state mirrors only earphone readbacks; it never persists requested values. */
final class ControlCoordinator {
    interface Row {
        void update(Integer actual, boolean enabled, boolean busy);
    }
    interface Status {
        void update(String message);
    }
    interface EqObserver { void update(EqualizerState state); }
    interface EqSaveObserver { void done(boolean confirmed, String details); }
    interface AiProbeObserver { void done(String result, AiKeyProbeCodec.State state); }
    interface AiWriteObserver { void done(boolean confirmed,String details); }
    interface AiStateObserver { void update(AiKeyProbeCodec.State rawState); }

    private final WeakReference<Activity> owner;
    private final BluetoothDevice device;
    private final EnumMap<FreeClipSetting, Row> rows = new EnumMap<>(FreeClipSetting.class);
    private final EnumMap<FreeClipSetting, Integer> known = new EnumMap<>(FreeClipSetting.class);
    private final Handler main = new Handler(Looper.getMainLooper());
    private Status status;
    private EqObserver eqObserver;
    private AiStateObserver aiObserver;
    private AiKeyProbeCodec.State aiState;
    private EqualizerState eqState;
    // One-session rollback snapshot; never persist a requested/assumed state as an earbud state.
    private EqualizerState undoBackup;
    private EqualizerState undoExpected;
    private boolean busy = false;
    private volatile boolean active = true;
    private boolean hasRead = false;
    private long lastRefresh = 0;
    private long lastAutoAttempt = 0;
    private volatile long revision = 0;
    private static final long AUTO_RETRY_INTERVAL_MS = 12_000L;
    // Only a brief in-process display preview; all mutations still require fresh readback.
    private static final long PREVIEW_TTL_MS = 25_000L;
    private static final Object CACHE_LOCK = new Object();
    private static String cacheAddress;
    private static long cacheTime;
    private static EnumMap<FreeClipSetting, Integer> cacheKnown;
    private static EqualizerState cacheEq;
    private boolean previewOnly = false;

    ControlCoordinator(Activity activity, BluetoothDevice device) {
        owner = new WeakReference<>(activity);
        this.device = device;
        String address = addressOf(device);
        synchronized (CACHE_LOCK) {
            if (address != null && address.equals(cacheAddress) && cacheKnown != null
                    && android.os.SystemClock.elapsedRealtime() - cacheTime < PREVIEW_TTL_MS) {
                known.putAll(cacheKnown);
                eqState = cacheEq;
                previewOnly = true;
            }
        }
    }

    private static String addressOf(BluetoothDevice device) {
        try { return device == null ? null : device.getAddress(); }
        catch (SecurityException ignored) { return null; }
    }

    private void cacheVerifiedState() {
        String address = addressOf(device);
        if (address == null || known.isEmpty()) return;
        synchronized (CACHE_LOCK) {
            cacheAddress = address;
            cacheTime = android.os.SystemClock.elapsedRealtime();
            cacheKnown = new EnumMap<>(known);
            cacheEq = eqState;
        }
    }

    private void invalidateCache() {
        String address = addressOf(device);
        synchronized (CACHE_LOCK) {
            if (address != null && address.equals(cacheAddress)) {
                cacheAddress = null;
                cacheKnown = null;
                cacheEq = null;
                cacheTime = 0;
            }
        }
    }

    void bind(FreeClipSetting item, Row row) {
        rows.put(item, row);
        updateOne(item);
    }
    void bindStatus(Status listener) {
        status = listener;
        status(device == null ? "未取得耳机蓝牙设备，控件处于只读状态"
                : previewOnly ? "显示上次已验证状态（只读） · 正在重新同步" : "等待读取耳机状态");
    }

    Integer get(FreeClipSetting item) { return known.get(item); }
    EqualizerState getEqState() { return eqState; }
    AiKeyProbeCodec.State getAiState() { return aiState; }
    void bindAiState(AiStateObserver observer) { aiObserver=observer; updateAi(); }
    private void updateAi() {
        if (aiObserver != null) try { aiObserver.update(aiState); }
        catch (Throwable t) { Log.w(ModuleConstants.TAG,"AI raw-state UI update failed",t); }
    }
    boolean canUndoEqualizer() {
        return !busy && !previewOnly && undoBackup != null && undoExpected != null
                && DirectEqualizerCodec.sameSnapshot(eqState, undoExpected)
                && EqSavePolicy.canRestore(eqState, undoBackup);
    }
    void bindEqualizer(EqObserver observer) {
        eqObserver = observer;
        updateEq();
    }
    private void updateEq() {
        if (eqObserver == null) return;
        try { eqObserver.update(eqState); }
        catch (Throwable t) { Log.w(ModuleConstants.TAG, "EQ UI update failed", t); }
    }
    boolean isBusy() { return busy || previewOnly; }

    /** Single automatic read when the page becomes visible, with a timeout-safe cooldown. */
    void autoRefresh() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (!alive() || busy) return;
        if (lastAutoAttempt != 0 && now - lastAutoAttempt < AUTO_RETRY_INTERVAL_MS) return;
        lastAutoAttempt = now;
        refresh();
    }

    /** Called only for ACL disconnects matching this panel's earphone. */
    void onDisconnected() {
        FreeClipTransport.onBluetoothDisconnected();
        invalidateCache();
        previewOnly = false;
        revision++; // Ignore the completion of an older in-flight read/write.
        busy = false;
        hasRead = false;
        lastRefresh = 0;
        lastAutoAttempt = 0;
        known.clear();
        eqState = null;
        aiState = null;
        updateAi();
        undoBackup = null;
        undoExpected = null;
        updateEq();
        status("耳机连接已断开 · 重连后自动同步");
        updateAll();
    }

    /** Do not apply an in-flight result to a page that is no longer visible. */
    void onPaused() {
        active = false;
        // Release the short-lived vendor RFCOMM channel when leaving the host page.
        FreeClipTransport.onPagePaused();
        if (busy) {
            revision++;
            busy = false;
            hasRead = false;
            lastRefresh = 0;
            lastAutoAttempt = 0;
            known.clear();
            eqState = null;
            aiState = null;
            previewOnly = false;
            updateAi();
            updateEq();
            status("同步已暂停 · 返回页面后自动刷新");
            updateAll();
        }
        // ACL events are not observed while paused. Require a new read before editing.
        hasRead = false;
        lastRefresh = 0;
        lastAutoAttempt = 0;
        previewOnly = !known.isEmpty();
        updateAll();
    }

    void onResumed() { active = true; }

    void onReconnected() {
        lastAutoAttempt = 0;
        status("耳机已重新连接 · 正在准备同步设置…");
    }

    void refresh() {
        if (busy || !alive()) return;
        if (device == null) {
            status("未识别耳机蓝牙地址；请重新从设备详情页进入");
            updateAll();
            return;
        }
        if (hasRead && android.os.SystemClock.elapsedRealtime() - lastRefresh < 2500) return;
        busy = true;
        final long currentRevision = ++revision;
        status("正在自动同步耳机设置…");
        updateAll();
        FreeClipTransport.read(device,
            (partial, eq, stage) -> main.post(() -> {
                if (!alive() || currentRevision != revision) return;
                // Show a decoded phase immediately without unlocking the controls mid-sync.
                known.putAll(partial);
                if (eq != null) eqState = eq;
                previewOnly = true;
                updateEq();
                status("已读取 " + stage + " · 其余设置继续同步…");
                updateAll();
            }),
            (values, eq, ai, error) -> main.post(() -> {
                if (!alive() || currentRevision != revision) return;
                busy = false;
                lastRefresh = android.os.SystemClock.elapsedRealtime();
                hasRead = error == null;
                known.clear();
                known.putAll(values);
                eqState = eq;
                // AI is read only by the explicit user-triggered probe, not on every opening.
                if (ai != null) aiState = ai;
                previewOnly = false;
                updateAi();
                if (undoExpected != null && !DirectEqualizerCodec.sameSnapshot(eq, undoExpected)) {
                    undoBackup = null;
                    undoExpected = null;
                }
                updateEq();
                if (error == null) {
                    cacheVerifiedState();
                    status("已回读 " + known.size() + " 项状态"
                            + (eq != null ? " · EQ 状态已读取" : " · EQ 未返回详细状态"));
                } else {
                    invalidateCache();
                    status(error + "；点击刷新重试");
                }
                updateAll();
                Log.i(ModuleConstants.TAG, "FreeClip2 state readback count=" + known.size());
            }));
    }

    void change(FreeClipSetting item, int desired) {
        if (item == FreeClipSetting.SOUND_EFFECT) {
            changeSoundPreset(desired);
            return;
        }
        if (busy || previewOnly || device == null || !alive() || !item.accepts(desired) || !known.containsKey(item)) return;
        final Integer before = known.get(item);
        if (before != null && before == desired) return;
        invalidateCache();
        busy = true;
        final long currentRevision = ++revision;
        status("正在修改「" + item.title + "」并等待耳机确认…");
        updateAll();
        FreeClipTransport.write(device, item, desired, (verified, actual, error) -> main.post(() -> {
            if (!alive() || currentRevision != revision) return;
            busy = false;
            if (actual != null) known.put(item, actual);
            else known.remove(item); // A failed transaction leaves the actual device value unknown.
            if (verified) {
                cacheVerifiedState();
                status("「" + item.title + "」已通过耳机状态回读确认");
            } else {
                status("「" + item.title + "」设置未确认：" + (error == null ? "无有效回读" : error));
                Activity activity = owner.get();
                if (activity != null) Toast.makeText(activity, "未确认生效，请检查耳机连接", Toast.LENGTH_SHORT).show();
            }
            lastRefresh = 0;
            updateAll();
        }));
    }

    /** Uses detailed EQ state, not a substring match or stale local preference. */
    void changeSoundPreset(int desired) {
        if (busy || previewOnly || device == null || !alive()
                || !SoundPresetCatalog.canWrite(eqState,desired)) return;
        if (eqState.selectedId == desired) return;
        invalidateCache();
        final EqualizerState snapshot = eqState;
        final long generation = ++revision;
        busy = true;
        status("正在复核并切换耳机内置音效…");
        updateAll();
        FreeClipTransport.writeSoundPreset(device,snapshot,desired,
                () -> generation == revision && alive(),
                (confirmed, observed, message) -> main.post(() -> {
                    if (!alive() || generation != revision) return;
                    busy = false;
                    // Clear stale known values if the device has reported a different state.
                    if (observed != null && observed.supported) {
                        eqState = observed;
                        known.put(FreeClipSetting.SOUND_EFFECT,observed.selectedId);
                        undoBackup = null;
                        undoExpected = null;
                    }
                    updateEq();
                    hasRead = observed != null;
                    lastRefresh = 0;
                    if (confirmed) cacheVerifiedState();
                    status(confirmed ? "内置音效已通过设备回读确认"
                            : "切换音效未确认：" + message);
                    updateAll();
                }));
    }

    /** Optional read-only probe. No guesses are persisted as earbud AI actions. */
    void probeAiKey(AiProbeObserver observer) {
        if (busy || previewOnly || device == null || !alive()) {
            observer.done("耳机未连接或正在同步",null);return;
        }
        busy=true;
        final long requestRevision=++revision;
        status("正在读取 AI 键设置…");
        updateAll();
        FreeClipTransport.probeAiKey(device, (report, raw) -> main.post(() -> {
            if (!alive() || requestRevision != revision)return;
            busy=false;
            status(raw == null ? "AI 键读取失败，请重试" : "AI 键设置已同步");
            updateAll();
            aiState = raw; updateAi(); // Do not leave stale actions editable after a failed read.
            observer.done(report,raw);
        }));
    }

    /** User-verified FreeClip 2 AI write with a fresh state preflight and exact CRC readback. */
    void changeAiKey(int side,int desired,AiWriteObserver observer) {
        if (busy || previewOnly || device == null || !alive() ||
                !AiKeyActionCodec.sideAllowed(side) || !AiKeyActionCodec.actionAllowed(desired) ||
                !AiKeyActionCodec.readable(aiState)) {
            observer.done(false,"请先刷新 AI 键状态，目前只支持「唤醒智能体／无」两种操作");
            return;
        }
        if (AiKeyActionCodec.value(aiState,side)==desired) {
            observer.done(true,"该侧已经是所选设置，无需发送"); return;
        }
        busy=true;
        final long requestRevision=++revision;
        final AiKeyProbeCodec.State snapshot=aiState;
        status("正在设置 AI 键并等待耳机状态回读…");
        updateAll();
        FreeClipTransport.writeAiKey(device,snapshot,side,desired,
                () -> requestRevision==revision && alive(),
                (confirmed,observed,error) -> main.post(() -> {
                    if (!alive() || requestRevision!=revision) return;
                    busy=false;
                    // A changed state is displayed only if the device actually reported it.
                    aiState=observed; updateAi();
                    status(confirmed ? "AI 键已保存，左右状态回读一致" : "AI 键写入未确认："+error);
                    updateAll();
                    observer.done(confirmed, confirmed ? "已从耳机回读目标动作" : error);
                }));
    }

    /** Full 10-band independent EQ save. No Huawei process, service or resources. */
    void saveEqualizer(int presetId, String name, int[] gains, EqSaveObserver observer) {
        Activity activity = owner.get();
        if (busy || previewOnly || device == null || activity == null || !alive()
                || !EqSavePolicy.fullAllowed(eqState,presetId,name,gains)) {
            observer.done(false,"只允许修改当前选中的自定义预设，且名称不超过 24 字节");
            return;
        }
        invalidateCache();
        busy = true;
        final long requestRevision = ++revision;
        final EqualizerState snapshot = eqState;
        // An in-flight or subsequent write invalidates an older undo transaction.
        undoBackup = null;
        undoExpected = null;
        status("通过独立 RFCOMM 通道复核并保存十段 EQ…");
        updateAll();
        FreeClipTransport.writeEqualizer(device, snapshot,presetId,name,gains,
                () -> revision == requestRevision && alive(),
                (confirmed, observed, message) -> main.post(() -> {
            if (!alive() || requestRevision != revision) return;
            busy = false;
            // Use only a real device readback, never manufacture a success state.
            eqState = observed;
            if (confirmed && observed != null) {
                undoBackup = snapshot;
                undoExpected = observed;
            }
            updateEq();
            if (confirmed) cacheVerifiedState();
            if (confirmed) status("十段 EQ 已通过耳机回读确认 · 可撤销本次保存");
            else status("独立 EQ 未确认：" + message);
            lastRefresh = 0;
            hasRead = observed != null;
            updateAll();
            observer.done(confirmed, message == null ? "写入已验证" : message);
        }));
    }

    /** Multi-slot operations never guess UI state; only device's 2B/4A reply is displayed. */
    void createCustomPreset(int id,String name,int[] gains,EqSaveObserver callback){
        if(busy||previewOnly||device==null||!alive()||
                !CustomPresetPolicy.createAllowed(eqState,id,name,gains)){
            callback.done(false,"预设列表尚未读取、槽位占用或输入无效");return;
        }
        final EqualizerState snapshot=eqState;
        invalidateCache();busy=true;
        final long generation=++revision;
        status("正在创建自定义音效，等待耳机完整回读…");updateAll();
        FreeClipTransport.createCustomPreset(device,snapshot,id,name,gains,
                () -> generation==revision&&alive(),
                (ok,observed,error) -> main.post(() -> {
                    if(!alive()||generation!=revision)return;
                    busy=false;
                    undoBackup=null;undoExpected=null;
                    eqState=observed;
                    if(observed!=null)known.put(FreeClipSetting.SOUND_EFFECT,observed.selectedId);
                    hasRead=observed!=null;lastRefresh=0;
                    if(ok)cacheVerifiedState();else invalidateCache();
                    updateEq();updateAll();
                    status(ok?"已创建新自定义音效，耳机返回完整预设列表":"创建预设未确认："+error);
                    callback.done(ok,ok?"耳机已确认新预设及全部十段增益":error);
                }));
    }
    void selectCustomPreset(int id,EqSaveObserver callback){
        if(busy||previewOnly||device==null||!alive()||
                !CustomPresetPolicy.switchAllowed(eqState,id)){
            callback.done(false,"目标槽位不存在或耳机状态尚未同步");return;
        }
        final EqualizerState snapshot=eqState;
        invalidateCache();busy=true;
        final long generation=++revision;
        status("正在切换自定义音效，核对原始预设…");updateAll();
        FreeClipTransport.selectCustomPreset(device,snapshot,id,
                () -> generation==revision&&alive(),
                (ok,observed,error) -> main.post(() -> {
                    if(!alive()||generation!=revision)return;
                    busy=false;undoBackup=null;undoExpected=null;eqState=observed;
                    if(observed!=null)known.put(FreeClipSetting.SOUND_EFFECT,observed.selectedId);
                    hasRead=observed!=null;lastRefresh=0;
                    if(ok)cacheVerifiedState();else invalidateCache();
                    updateEq();updateAll();
                    status(ok?"已切换至耳机确认的自定义音效":"切换未确认："+error);
                    callback.done(ok,ok?"已读取目标名称及全部十段增益":error);
                }));
    }
    void deleteCustomPreset(int id,EqSaveObserver callback){
        if(busy||previewOnly||device==null||!alive()||
                !CustomPresetPolicy.deleteAllowed(eqState,id)){
            callback.done(false,"删除仅允许非当前选中的已保存槽位");return;
        }
        final EqualizerState snapshot=eqState;
        invalidateCache();busy=true;
        final long generation=++revision;
        status("正在删除未选中音效并核查剩余预设…");updateAll();
        FreeClipTransport.deleteCustomPreset(device,snapshot,id,
                () -> generation==revision&&alive(),
                (ok,observed,error) -> main.post(() -> {
                    if(!alive()||generation!=revision)return;
                    busy=false;undoBackup=null;undoExpected=null;eqState=observed;
                    if(observed!=null)known.put(FreeClipSetting.SOUND_EFFECT,observed.selectedId);
                    hasRead=observed!=null;lastRefresh=0;
                    if(ok)cacheVerifiedState();else invalidateCache();
                    updateEq();updateAll();
                    status(ok?"耳机已确认移除目标自定义预设":"删除未确认："+error);
                    callback.done(ok,ok?"目标 ID 从耳机预设列表消失，其他槽位保持一致":error);
                }));
    }

    /** Roll back only when the currently confirmed earbud state matches the saved transaction. */
    void undoEqualizer(EqSaveObserver observer) {
        if (!canUndoEqualizer() || !alive() || device == null) {
            observer.done(false, "无可恢复的记录；请重新读取耳机状态");
            return;
        }
        final EqualizerState current = eqState;
        final EqualizerState backup = undoBackup;
        final int[] target = EqSavePolicy.toArray(backup.selectedGains);
        if (!EqSavePolicy.canRestore(current, backup) || target == null) {
            observer.done(false, "备份与当前耳机状态不匹配");
            return;
        }
        invalidateCache();
        busy = true;
        final long currentRevision = ++revision;
        status("正在复核并恢复上次保存前的 EQ 曲线…");
        updateAll();
        FreeClipTransport.writeEqualizer(device, current, backup.selectedId,
                backup.selectedName, target,
                () -> revision == currentRevision && alive(),
                (confirmed, observed, message) -> main.post(() -> {
                    if (!alive() || currentRevision != revision) return;
                    busy = false;
                    eqState = observed;
                    // One-step rollback, not an unbounded undo/redo stack.
                    undoBackup = null;
                    undoExpected = null;
                    updateEq();
                    lastRefresh = 0;
                    hasRead = observed != null;
                    if (confirmed) cacheVerifiedState();
                    status(confirmed ? "已恢复上次保存前的 EQ，耳机回读确认" : "恢复未确认：" + message);
                    updateAll();
                    observer.done(confirmed, confirmed ? "耳机已确认恢复" : message);
                }));
    }

    private void updateAll() {
        for (FreeClipSetting item : rows.keySet()) updateOne(item);
    }

    private void updateOne(FreeClipSetting item) {
        Row row = rows.get(item);
        if (row == null) return;
        Integer state = known.get(item);
        boolean enabled = item.supported && device != null && state != null && !busy && !previewOnly && active;
        if (item == FreeClipSetting.SOUND_EFFECT)
            enabled = enabled && !SoundPresetCatalog.writableOptions(eqState).isEmpty();
        try { row.update(state, enabled, busy); }
        catch (Throwable t) { Log.w(ModuleConstants.TAG, "UI update failed for " + item, t); }
    }

    private boolean alive() {
        Activity activity = owner.get();
        return active && activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void status(String text) {
        try { if (status != null) status.update(text); }
        catch (Throwable t) { Log.w(ModuleConstants.TAG, "UI status update failed", t); }
    }
}
