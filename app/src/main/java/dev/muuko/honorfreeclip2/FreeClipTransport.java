package dev.muuko.honorfreeclip2;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Single-worker, short-lived RFCOMM/SPP transactions. No hooks in Bluetooth or system_server.
 * The Bluetooth device must be confirmed by DeviceMatcher before calling this class.
 * This is an independent experimental transport; do not run simultaneous tools that own SPP.
 */
final class FreeClipTransport {
    interface ReadCallback { void done(Map<FreeClipSetting, Integer> states, EqualizerState eq,
                                      AiKeyProbeCodec.State ai, String error); }
    interface WriteCallback { void done(boolean verified, Integer actual, String error); }
    /** Results are progressively delivered but never marked verified until each frame is CRC-checked. */
    interface ProgressCallback { void done(Map<FreeClipSetting, Integer> values, EqualizerState eq, String stage); }

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "HonorFreeClip2-rfcomm");
        thread.setDaemon(true);
        return thread;
    });
    private static final long CONNECT_TIMEOUT = 2600;
    private static final long READ_WINDOW = 1550;
    private static final long COMBINED_WINDOW = 1400;
    private static final long WRITE_SETTLE_MS = 80;
    private static final Object POOL_LOCK = new Object();
    private static final ScheduledExecutorService IDLE_EVICTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "HonorFreeClip2-rfcomm-idle");
        thread.setDaemon(true);
        return thread;
    });
    private static BluetoothSocket pooledSocket;
    private static String pooledAddress;
    private static long pooledIdleAt;
    private static boolean pooledBorrowed;
    private static boolean closeAfterRelease;
    private static long poolEpoch;
    private static long evictionSequence;
    private FreeClipTransport() {}

    private static Map<FreeClipSetting, Integer> copyStates(Map<FreeClipSetting, Integer> states) {
        Map<FreeClipSetting, Integer> copy = new EnumMap<>(FreeClipSetting.class);
        copy.putAll(states);
        return copy;
    }

    static void read(BluetoothDevice device, ReadCallback callback) {
        read(device, null, callback);
    }

    /** Fast staged read. AI key is intentionally lazy: querying only when its sheet opens avoids slowing the homepage. */
    static void read(BluetoothDevice device, ProgressCallback progress, ReadCallback callback) {
        final long requestEpoch = currentEpoch();
        IO.execute(() -> {
            if (requestEpoch != currentEpoch()) {
                callback.done(new EnumMap<>(FreeClipSetting.class), null, null, "页面已退出，取消过期同步");
                return;
            }
            if (device == null) {
                callback.done(new EnumMap<>(FreeClipSetting.class), null, null, "未找到耳机蓝牙地址");
                return;
            }
            BluetoothSocket socket = null;
            boolean healthy = false;
            long started = android.os.SystemClock.elapsedRealtime();
            try {
                socket = acquire(device);
                long connectedAt = android.os.SystemClock.elapsedRealtime();
                // Send all three families over one existing SPP socket. The response
                // must still contain valid, CRC-checked state frames before display.
                byte[] combined = exchangeRead(socket, FreeClipProtocol.ALL_QUERIES, COMBINED_WINDOW);
                EnumMap<FreeClipSetting,Integer> states = new EnumMap<>(FreeClipSetting.class);
                states.putAll(FreeClipProtocol.decode(combined));
                EqualizerState eq = EqualizerCodec.parse(combined);
                if (eq != null) states.put(FreeClipSetting.SOUND_EFFECT, eq.selectedId);
                if (progress != null && (!states.isEmpty() || eq != null))
                    progress.done(copyStates(states), eq, "合并查询");
                long batchAt = android.os.SystemClock.elapsedRealtime();
                boolean recoveredBool = false, recoveredAudio = false, recoveredGesture = false;

                // Recover only groups which did not answer the combined query.
                // The old staged path remains the fallback for firmware variations.
                if (ReadBatchPolicy.booleanCount(states) < 3) {
                    byte[] reply = exchangeRead(socket, FreeClipProtocol.BOOL_QUERIES, READ_WINDOW);
                    states.putAll(FreeClipProtocol.decode(reply));
                    recoveredBool = true;
                    if (progress != null) progress.done(copyStates(states), eq, "智能功能补查");
                }
                if (!ReadBatchPolicy.hasAudio(states, eq)
                        || !states.containsKey(FreeClipSetting.SPATIAL_MODE)) {
                    byte[] reply = exchangeRead(socket, FreeClipProtocol.AUDIO_QUERIES, READ_WINDOW);
                    states.putAll(FreeClipProtocol.decode(reply));
                    EqualizerState observed = EqualizerCodec.parse(reply);
                    if (observed != null) eq = observed;
                    recoveredAudio = true;
                    if (progress != null) progress.done(copyStates(states), eq, "空间音频补查");
                }
                if (eq == null) {
                    // Retry detailed EQ only when the combined response had no EQ state.
                    try { eq = EqualizerCodec.parse(exchangeRead(socket, EqualizerCodec.QUERY, 1350)); }
                    catch (IOException ignored) { }
                }
                if (eq != null) states.put(FreeClipSetting.SOUND_EFFECT, eq.selectedId);
                if (ReadBatchPolicy.gestureCount(states) < 2) {
                    byte[] reply = exchangeRead(socket, FreeClipProtocol.GESTURE_QUERIES, READ_WINDOW);
                    states.putAll(FreeClipProtocol.decode(reply));
                    recoveredGesture = true;
                }
                if (progress != null) progress.done(copyStates(states), eq, "完成");
                healthy = !states.isEmpty() || eq != null;
                callback.done(states, eq, null, states.isEmpty() && eq == null
                        ? "耳机未返回可识别的设置状态" : null);
                Log.i(ModuleConstants.TAG, "FAST_SYNC_TIMING connect=" + (connectedAt-started)
                        + "ms batch=" + (batchAt-connectedAt) + "ms fallbackBool=" + recoveredBool
                        + " fallbackAudio=" + recoveredAudio + " fallbackGesture=" + recoveredGesture
                        + " total=" + (android.os.SystemClock.elapsedRealtime()-started) + "ms");
            } catch (Throwable t) {
                Log.w(ModuleConstants.TAG, "RFCOMM read failed: " + t.getClass().getSimpleName());
                callback.done(new EnumMap<>(FreeClipSetting.class), null, null, message(t));
            } finally { release(socket, healthy); }
        });
    }

    static void write(BluetoothDevice device, FreeClipSetting key, int desired, WriteCallback callback) {
        final long requestEpoch = currentEpoch();
        IO.execute(() -> {
            if (requestEpoch != currentEpoch()) {
                callback.done(false, null, "页面已退出，取消过期修改");
                return;
            }
            if (device == null || !key.accepts(desired)) {
                callback.done(false, null, "无效设备或操作"); return;
            }
            byte[] packet = FreeClipProtocol.writePacket(key, desired);
            byte[] query = FreeClipProtocol.queryFor(key);
            if (packet == null || query.length == 0) {
                callback.done(false, null, "该功能协议尚未适配"); return;
            }
            BluetoothSocket socket = null;
            boolean healthy = false;
            long started = android.os.SystemClock.elapsedRealtime();
            try {
                socket = acquire(device);
                long connectedAt = android.os.SystemClock.elapsedRealtime();
                // We do not need a 300 ms ACK read. Send once, allow a bounded
                // processing gap, then only accept a CRC-valid report for this key.
                if (requestEpoch != currentEpoch()) {
                    callback.done(false, null, "页面已退出，未发送指令");
                    return;
                }
                exchange(socket, packet, 0);
                Thread.sleep(WRITE_SETTLE_MS);
                Integer actual = FreeClipProtocol.decode(
                        exchangeReadSetting(socket, query, READ_WINDOW, key)).get(key);
                if (actual == null || actual != desired)
                    actual = FreeClipProtocol.decode(
                            exchangeReadSetting(socket, query, READ_WINDOW, key)).get(key);
                boolean verified = actual != null && actual == desired;
                healthy = actual != null;
                callback.done(verified, actual, verified ? null : "耳机状态回读未确认修改");
                Log.i(ModuleConstants.TAG, "FAST_WRITE_TIMING key=" + key
                        + " connect=" + (connectedAt-started) + "ms total="
                        + (android.os.SystemClock.elapsedRealtime()-started)
                        + "ms verified=" + verified);
            } catch (Throwable t) {
                Log.w(ModuleConstants.TAG, "RFCOMM write failed for " + key + ": " + t.getClass().getSimpleName());
                callback.done(false, null, message(t));
            } finally { release(socket, healthy); }
        });
    }

    interface PresetWriteCallback { void done(boolean verified, EqualizerState observed, String error); }

    /**
     * Built-in preset switch via verified 2B/49 command, with detailed 2B/4A
     * preflight and post-write state. A device-advertised ID is mandatory.
     */
    static void writeSoundPreset(BluetoothDevice device, EqualizerState snapshot,
                                 int desired, BooleanSupplier stillAllowed,
                                 PresetWriteCallback callback) {
        final long requestEpoch = currentEpoch();
        IO.execute(() -> {
            if (requestEpoch != currentEpoch()) {
                callback.done(false, null, "页面已退出，取消预设切换");
                return;
            }
            if (device == null || !SoundPresetCatalog.canWrite(snapshot, desired)) {
                callback.done(false, null, "耳机未确认支持该内置音效，禁止猜测写入");
                return;
            }
            if (!stillAllowed.getAsBoolean()) {
                callback.done(false, null, "页面已离开，取消写入"); return;
            }
            BluetoothSocket socket = null;
            boolean healthy = false;
            try {
                socket = acquire(device);
                EqualizerState current = EqualizerCodec.parse(exchangeRead(socket, EqualizerCodec.QUERY, READ_WINDOW));
                if (!SoundPresetCatalog.samePreflight(snapshot, current)
                        || !SoundPresetCatalog.canWrite(current, desired)) {
                    callback.done(false, current, "耳机当前音效或预设列表已变化，未发送写入");
                    return;
                }
                if (!stillAllowed.getAsBoolean() || requestEpoch != currentEpoch()) {
                    callback.done(false, current, "页面退出，写入取消"); return;
                }
                byte[] packet = FreeClipProtocol.writePacket(FreeClipSetting.SOUND_EFFECT, desired);
                if (packet == null) { callback.done(false, current, "没有该预设的已验证命令"); return; }
                exchange(socket, packet, 0);
                Thread.sleep(100);
                EqualizerState observed = EqualizerCodec.parse(exchangeRead(socket, EqualizerCodec.QUERY, READ_WINDOW));
                if (observed == null || observed.selectedId != desired)
                    observed = EqualizerCodec.parse(exchangeRead(socket, EqualizerCodec.QUERY, READ_WINDOW));
                boolean verified = observed != null && observed.supported && observed.selectedId == desired;
                healthy = observed != null;
                callback.done(verified, observed, verified ? null : "指令已发送，但耳机未回读目标音效 ID");
            } catch (Throwable e) {
                Log.w(ModuleConstants.TAG, "Sound preset switch failed",e);
                callback.done(false, null, message(e));
            } finally { release(socket, healthy); }
        });
    }

    interface AiProbeCallback { void done(String result, AiKeyProbeCodec.State state); }

    /** One-shot, read-only candidate long-press query. Never sends action writes. */
    static void probeAiKey(BluetoothDevice device, AiProbeCallback callback) {
        final long requestEpoch = currentEpoch();
        IO.execute(() -> {
            if (requestEpoch != currentEpoch()) {
                callback.done("页面已退出，取消只读探测",null);return;
            }
            if (device == null) {callback.done("没有有效的耳机连接",null);return;}
            BluetoothSocket socket = null;
            boolean healthy = false;
            try {
                socket = acquire(device);
                byte[] reply = exchangeRead(socket,AiKeyProbeCodec.QUERY,1600);
                healthy = true;
                callback.done(AiKeyProbeCodec.summarize(reply),AiKeyProbeCodec.parseState(reply));
            } catch (Throwable t) {
                callback.done("只读探测失败："+message(t),null);
            } finally {release(socket, healthy);}
        });
    }

    interface AiWriteCallback { void done(boolean confirmed,AiKeyProbeCodec.State observed,String error); }

    /**
     * 2B/16 command for the two independently observed FreeClip 2 values.
     * Its 00/FF direct write and 2B/17 readback have been user-verified.
     * The 2B/17 preflight and post-write checks are both mandatory.
     */
    static void writeAiKey(BluetoothDevice device,AiKeyProbeCodec.State snapshot,
                           int side,int desired,BooleanSupplier stillAllowed,AiWriteCallback callback) {
        final long requestEpoch=currentEpoch();
        IO.execute(() -> {
            if (requestEpoch!=currentEpoch() || device==null ||
                    !AiKeyActionCodec.readable(snapshot) ||
                    !AiKeyActionCodec.sideAllowed(side) ||
                    !AiKeyActionCodec.actionAllowed(desired) || !stillAllowed.getAsBoolean()) {
                callback.done(false,null,"状态已过期或参数无效，未发送写入");return;
            }
            byte[] packet=AiKeyActionCodec.verifiedWrite(side,desired);
            if (packet==null) {callback.done(false,null,"未能构造写入命令");return;}
            BluetoothSocket socket=null;
            boolean healthy=false;
            try {
                socket=acquire(device);
                AiKeyProbeCodec.State before=AiKeyProbeCodec.parseState(
                        exchangeRead(socket,AiKeyProbeCodec.QUERY,1500));
                if (!AiKeyActionCodec.sameSnapshot(snapshot,before)) {
                    healthy=before!=null;
                    callback.done(false,before,"配置已变化或读取不完整；未发送写入");return;
                }
                if (requestEpoch!=currentEpoch() || !stillAllowed.getAsBoolean()) {
                    healthy=true;
                    callback.done(false,before,"页面离开或耳机断开，取消写入");return;
                }
                exchange(socket,packet,0); // ONE candidate write, never blindly retry
                Thread.sleep(100);
                AiKeyProbeCodec.State observed=AiKeyProbeCodec.parseState(
                        exchangeRead(socket,AiKeyProbeCodec.QUERY,1500));
                if (!AiKeyActionCodec.verified(before,observed,side,desired)) {
                    AiKeyProbeCodec.State next=AiKeyProbeCodec.parseState(
                            exchangeRead(socket,AiKeyProbeCodec.QUERY,1500));
                    if (next!=null) observed=next;
                }
                boolean confirmed=AiKeyActionCodec.verified(before,observed,side,desired);
                healthy=observed!=null;
                callback.done(confirmed,observed,confirmed?null:
                        "AI 键命令已发送，但耳机未返回目标动作；未自动重复发送");
            } catch(Throwable t) {
                Log.w(ModuleConstants.TAG,"AI key write failed",t);
                callback.done(false,null,message(t));
            } finally {release(socket,healthy);}
        });
    }

    interface EqWriteCallback { void done(boolean verified, EqualizerState observed, String error); }

    /** One-shot independent write, preflight re-read, exact post-write CRC-verified readback. */
    static void writeEqualizer(BluetoothDevice device, EqualizerState snapshot,
                               int id, String name, int[] requested,
                               BooleanSupplier stillAllowed, EqWriteCallback callback) {
        final int[] gains = requested == null ? null : requested.clone();
        final long requestEpoch = currentEpoch();
        IO.execute(() -> {
            if (requestEpoch != currentEpoch()) {
                callback.done(false, null, "页面已退出，取消 EQ 写入");
                return;
            }
            if (device == null || !EqSavePolicy.fullAllowed(snapshot, id, name, gains)) {
                callback.done(false, null, "仅允许修改当前选中的自定义预设；名称最长 24 字节，10 段范围 ±6 dB");
                return;
            }
            if (!stillAllowed.getAsBoolean()) {
                callback.done(false,null,"页面已退出，写入取消");return;
            }
            byte[] packet = DirectEqualizerCodec.encode(id, name, gains);
            if (packet == null) { callback.done(false, null, "编码失败：EQ 参数不合法"); return; }
            BluetoothSocket socket = null;
            boolean healthy = false;
            try {
                socket = acquire(device);
                EqualizerState before = EqualizerCodec.parse(exchangeRead(socket, EqualizerCodec.QUERY, 1700));
                if (!DirectEqualizerCodec.sameSnapshot(before, snapshot)) {
                    callback.done(false, before, "写入前耳机 EQ 状态已变化或读取失败，未发送指令");
                    return;
                }
                if (!stillAllowed.getAsBoolean() || requestEpoch != currentEpoch()) {
                    callback.done(false,before,"写入前页面已离开或耳机已断开，取消发送");
                    return;
                }
                // The recovered 2B/49 payload, sent once only.
                // A successful output stream write or ACK is not a confirmation.
                exchange(socket, packet, 0);
                Thread.sleep(150);
                EqualizerState observed = EqualizerCodec.parse(exchangeRead(socket, EqualizerCodec.QUERY, 1700));
                if (!DirectEqualizerCodec.isExactReport(observed, id, name, gains)) {
                    EqualizerState retry = EqualizerCodec.parse(exchangeRead(socket, EqualizerCodec.QUERY, 1700));
                    if (retry != null) observed = retry;
                }
                boolean verified = DirectEqualizerCodec.isExactReport(observed,id,name,gains);
                healthy = observed != null;
                callback.done(verified, observed, verified ? null
                        : "已发送一次写入，但没有收到完全匹配的耳机回读；请检查实际音效");
            } catch (Throwable failure) {
                Log.w(ModuleConstants.TAG, "Independent EQ write failed", failure);
                callback.done(false, null, message(failure));
            } finally { release(socket, healthy); }
        });
    }

    /**
     * User-driven multi-preset transaction. For update/create the vendor 2B/49
     * operation=1 is used. For delete, operation=2 is independently documented
     * by OpenFreebuds, but not yet verified on this FreeClip 2 firmware.
     * Every operation is single-shot, and success is confirmed from new 2B/4A
     * state including the complete TLV08 custom catalog.
     */
    static void createCustomPreset(BluetoothDevice device, EqualizerState snapshot,
                                   int id, String name, int[] requested,
                                   BooleanSupplier stillAllowed, EqWriteCallback callback) {
        final int[] gains = requested == null ? null : requested.clone();
        final long epoch = currentEpoch();
        IO.execute(() -> {
            if (device == null || epoch != currentEpoch() || !stillAllowed.getAsBoolean()
                    || !CustomPresetPolicy.createAllowed(snapshot, id, name, gains)) {
                callback.done(false, null, "槽位已占用、预设列表未读取或请求过期；未发送创建命令"); return;
            }
            BluetoothSocket socket = null;
            boolean healthy = false;
            try {
                socket = acquire(device);
                EqualizerState before = EqualizerCodec.parse(
                        exchangeRead(socket,EqualizerCodec.QUERY,1700));
                healthy = before != null;
                if (!CustomPresetPolicy.sameCatalog(snapshot,before)
                        || !CustomPresetPolicy.createAllowed(before,id,name,gains)) {
                    callback.done(false,before,"预设列表已变化或读取失败，拒绝创建");return;
                }
                if (!stillAllowed.getAsBoolean() || epoch!=currentEpoch()) {
                    callback.done(false,before,"页面退出，未发送创建命令");return;
                }
                byte[] packet = DirectEqualizerCodec.encode(id,name,gains);
                if(packet==null){callback.done(false,before,"无法编码新预设");return;}
                exchange(socket,packet,0);
                Thread.sleep(140);
                EqualizerState observed=EqualizerCodec.parse(
                        exchangeRead(socket,EqualizerCodec.QUERY,1700));
                if (!CustomPresetPolicy.created(before,observed,id,name,gains)) {
                    EqualizerState again=EqualizerCodec.parse(
                            exchangeRead(socket,EqualizerCodec.QUERY,1700));
                    if(again!=null)observed=again;
                }
                boolean verified=CustomPresetPolicy.created(before,observed,id,name,gains);
                healthy=observed!=null;
                Log.i(ModuleConstants.TAG,"CUSTOM_PRESET_ACTION op=create id="+id+" confirmed="+verified);
                callback.done(verified,observed,verified?null:
                        "已发送一次创建指令，但耳机未报告完整的新预设槽位；未自动重试");
            } catch(Throwable e){
                Log.w(ModuleConstants.TAG,"Create EQ preset failed",e);
                callback.done(false,null,message(e));
            } finally{release(socket,healthy);}
        });
    }

    static void selectCustomPreset(BluetoothDevice device,EqualizerState snapshot,int id,
                                   BooleanSupplier stillAllowed,EqWriteCallback callback) {
        final long epoch=currentEpoch();
        IO.execute(() -> {
            if(device==null||epoch!=currentEpoch()||!stillAllowed.getAsBoolean()
                    ||!CustomPresetPolicy.switchAllowed(snapshot,id)){
                callback.done(false,null,"目标预设不存在或状态尚未同步，未发送命令");return;
            }
            BluetoothSocket socket=null;
            boolean healthy=false;
            try{
                socket=acquire(device);
                EqualizerState before=EqualizerCodec.parse(
                        exchangeRead(socket,EqualizerCodec.QUERY,1700));
                healthy=before!=null;
                if(!CustomPresetPolicy.sameCatalog(snapshot,before)
                        ||!CustomPresetPolicy.switchAllowed(before,id)){
                    callback.done(false,before,"耳机当前预设列表已变化，拒绝覆盖");return;
                }
                EqualizerState.Preset target=CustomPresetPolicy.lookup(before,id);
                byte[] packet=DirectEqualizerCodec.encode(id,target.name,
                        EqSavePolicy.toArray(target.gains));
                if(packet==null||!stillAllowed.getAsBoolean()||epoch!=currentEpoch()){
                    callback.done(false,before,"切换前操作被取消");return;
                }
                // Using the full custom-preset op=1, as OpenFreebuds does when selecting
                // a custom slot, rather than assuming a built-in 2B/49 short form works.
                exchange(socket,packet,0);
                Thread.sleep(125);
                EqualizerState observed=EqualizerCodec.parse(
                        exchangeRead(socket,EqualizerCodec.QUERY,1700));
                if(!CustomPresetPolicy.switched(before,observed,id)){
                    EqualizerState next=EqualizerCodec.parse(
                            exchangeRead(socket,EqualizerCodec.QUERY,1700));
                    if(next!=null)observed=next;
                }
                boolean verified=CustomPresetPolicy.switched(before,observed,id);
                healthy=observed!=null;
                Log.i(ModuleConstants.TAG,"CUSTOM_PRESET_ACTION op=select id="+id+" confirmed="+verified);
                callback.done(verified,observed,verified?null:
                        "耳机未回读目标槽位及原始曲线；未重复写入");
            } catch(Throwable e){
                Log.w(ModuleConstants.TAG,"Select custom EQ preset failed",e);
                callback.done(false,null,message(e));
            } finally{release(socket,healthy);}
        });
    }

    static void deleteCustomPreset(BluetoothDevice device,EqualizerState snapshot,int id,
                                   BooleanSupplier stillAllowed,EqWriteCallback callback) {
        final long epoch=currentEpoch();
        IO.execute(() -> {
            if(device==null||epoch!=currentEpoch()||!stillAllowed.getAsBoolean()
                    ||!CustomPresetPolicy.deleteAllowed(snapshot,id)){
                callback.done(false,null,"当前选中槽位或预设列表未知，禁止删除");return;
            }
            BluetoothSocket socket=null;
            boolean healthy=false;
            try{
                socket=acquire(device);
                EqualizerState before=EqualizerCodec.parse(
                        exchangeRead(socket,EqualizerCodec.QUERY,1700));
                healthy=before!=null;
                if(!CustomPresetPolicy.sameCatalog(snapshot,before)
                        ||!CustomPresetPolicy.deleteAllowed(before,id)){
                    callback.done(false,before,"预设列表已变化，拒绝删除");return;
                }
                EqualizerState.Preset target=CustomPresetPolicy.lookup(before,id);
                byte[] packet=DirectEqualizerCodec.encodeDelete(id,target.name,
                        EqSavePolicy.toArray(target.gains));
                if(packet==null || epoch!=currentEpoch()||!stillAllowed.getAsBoolean()){
                    callback.done(false,before,"删除前操作被取消");return;
                }
                exchange(socket,packet,0); // one irreversible candidate operation, never retry
                Thread.sleep(145);
                EqualizerState observed=EqualizerCodec.parse(
                        exchangeRead(socket,EqualizerCodec.QUERY,1700));
                if(!CustomPresetPolicy.deleted(before,observed,id)){
                    EqualizerState next=EqualizerCodec.parse(
                            exchangeRead(socket,EqualizerCodec.QUERY,1700));
                    if(next!=null)observed=next;
                }
                boolean verified=CustomPresetPolicy.deleted(before,observed,id);
                healthy=observed!=null;
                Log.i(ModuleConstants.TAG,"CUSTOM_PRESET_ACTION op=delete id="+id+" confirmed="+verified);
                callback.done(verified,observed,verified?null:
                        "已发送一次删除指令，但耳机未确认该槽位移除；请刷新，不要重复点删除");
            } catch(Throwable e){
                Log.w(ModuleConstants.TAG,"Delete custom EQ preset failed",e);
                callback.done(false,null,message(e));
            } finally{release(socket,healthy);}
        });
    }

    private static String message(Throwable t) {
        if (t instanceof SecurityException) return "缺少蓝牙连接权限";
        if (t instanceof java.util.concurrent.TimeoutException) return "蓝牙控制通道连接超时";
        if (t instanceof IOException && t.getMessage() != null
                && t.getMessage().contains("过期建链")) return "页面切换后取消过期蓝牙请求";
        if (t instanceof IOException) return "蓝牙控制通道不可用（可能被其他程序占用）";
        return "耳机通信失败：" + t.getClass().getSimpleName();
    }

    private static long currentEpoch() {
        synchronized (POOL_LOCK) { return poolEpoch; }
    }

    /** Release cached control socket on activity pause, without cutting an active verification in half. */
    static void onPagePaused() {
        synchronized (POOL_LOCK) {
            poolEpoch++;
            closeAfterRelease = true;
            if (!pooledBorrowed) clearPoolLocked();
        }
    }

    /** ACL loss invalidates the socket immediately; the pending read/write will fail safely. */
    static void onBluetoothDisconnected() {
        synchronized (POOL_LOCK) {
            poolEpoch++;
            closeAfterRelease = true;
            clearPoolLocked();
        }
    }

    private static BluetoothSocket acquire(BluetoothDevice device) throws Exception {
        final String address = device.getAddress();
        final long now = android.os.SystemClock.elapsedRealtime();
        final long epoch;
        synchronized (POOL_LOCK) {
            epoch = poolEpoch;
            if (pooledSocket != null && SocketReusePolicy.eligible(address, pooledAddress,
                    pooledSocket.isConnected(), pooledBorrowed, now-pooledIdleAt,
                    closeAfterRelease)) {
                pooledBorrowed = true;
                evictionSequence++;  // Cancel any pending idle-eviction timer.
                Log.i(ModuleConstants.TAG, "RFCOMM_POOL reused connection");
                return pooledSocket;
            }
            if (!pooledBorrowed) clearPoolLocked();
        }
        BluetoothSocket fresh = connect(device);
        synchronized (POOL_LOCK) {
            // Do not resurrect a stale connection after a pause or ACL disconnect
            // that happened while BluetoothSocket.connect() was blocked.
            if (epoch != poolEpoch) {
                close(fresh);
                throw new IOException("控制页面已退出或蓝牙连接变化，取消过期建链");
            }
            pooledSocket = fresh;
            pooledAddress = address;
            pooledBorrowed = true;
            closeAfterRelease = false;
            evictionSequence++;
            Log.i(ModuleConstants.TAG, "RFCOMM_POOL opened new connection");
            return fresh;
        }
    }

    private static void release(BluetoothSocket socket, boolean healthy) {
        if (socket == null) return;
        synchronized (POOL_LOCK) {
            if (pooledSocket != socket) { close(socket); return; }
            pooledBorrowed = false;
            if (!healthy || !socket.isConnected() || closeAfterRelease) {
                clearPoolLocked();
                return;
            }
            pooledIdleAt = android.os.SystemClock.elapsedRealtime();
            final long token = ++evictionSequence;
            IDLE_EVICTOR.schedule(() -> {
                synchronized (POOL_LOCK) {
                    if (evictionSequence == token && pooledSocket == socket
                            && !pooledBorrowed) clearPoolLocked();
                }
            }, SocketReusePolicy.IDLE_TTL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private static void clearPoolLocked() {
        BluetoothSocket old = pooledSocket;
        pooledSocket = null;
        pooledAddress = null;
        pooledIdleAt = 0;
        pooledBorrowed = false;
        evictionSequence++;
        close(old);
    }

    private static BluetoothSocket connect(BluetoothDevice device) throws Exception {
        Exception lastError = null;
        // HuaweiPods uses this classic Bluetooth SPP UUID first, with secure and insecure fallback.
        for (int attempt = 0; attempt < 2; attempt++) {
            BluetoothSocket socket = null;
            try {
                socket = attempt == 0
                        ? device.createRfcommSocketToServiceRecord(SPP)
                        : device.createInsecureRfcommSocketToServiceRecord(SPP);
                if (tryConnect(socket)) return socket;
            } catch (Exception e) { lastError = e; }
            close(socket);
        }
        // An optional SDP fallback for devices advertising vendor control UUIDs.
        ParcelUuid[] uuids = null;
        try { uuids = device.getUuids(); } catch (Throwable ignored) { }
        if (uuids != null) {
            int candidates = 0;
            for (ParcelUuid parcel : uuids) {
                if (parcel == null || SPP.equals(parcel.getUuid()) || !isControlCandidate(parcel.getUuid())) continue;
                if (++candidates > 2) break; // bounded work, no 1..8 blind channel probing
                BluetoothSocket socket = null;
                try {
                    socket = device.createRfcommSocketToServiceRecord(parcel.getUuid());
                    if (tryConnect(socket)) return socket;
                } catch (Exception e) { lastError = e; }
                close(socket);
            }
        }
        throw lastError == null ? new IOException("SPP connection failed") : lastError;
    }

    /** Exclude classic HFP/A2DP/AVRCP UUIDs, which must never receive vendor control frames. */
    private static boolean isControlCandidate(UUID uuid) {
        String value = uuid.toString().toLowerCase(java.util.Locale.ROOT);
        if (!value.startsWith("0000") || !value.endsWith("-0000-1000-8000-00805f9b34fb")) return true;
        int id;
        try { id = Integer.parseInt(value.substring(4, 8), 16); } catch (Exception e) { return false; }
        switch (id) {
            case 0x1108: case 0x110A: case 0x110B: case 0x110C: case 0x110D:
            case 0x110E: case 0x110F: case 0x1112: case 0x111E: case 0x111F: return false;
            default: return true;
        }
    }

    private static boolean tryConnect(BluetoothSocket socket) throws Exception {
        ExecutorService connectExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "HonorFreeClip2-connect");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<Boolean> job = connectExecutor.submit(new Callable<Boolean>() {
                @Override public Boolean call() throws Exception { socket.connect(); return true; }
            });
            try { return job.get(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS); }
            catch (java.util.concurrent.TimeoutException timedOut) {
                job.cancel(true);
                close(socket); // abort the blocked connect call
                throw timedOut;
            } catch (java.util.concurrent.ExecutionException wrapped) {
                Throwable cause = wrapped.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                throw new IOException("socket connect failed", cause);
            }
        } finally { connectExecutor.shutdownNow(); }
    }

    private static byte[] exchangeRead(BluetoothSocket socket, byte[] query, long maxWindowMs) throws Exception {
        return exchange(socket, query, maxWindowMs, true);
    }

    /** For writes, finish only when the exact requested setting returns a valid state frame. */
    private static byte[] exchangeReadSetting(BluetoothSocket socket, byte[] query,
                                              long maxWindowMs, FreeClipSetting key) throws Exception {
        return exchangeInternal(socket, query, maxWindowMs, true, key);
    }

    private static byte[] exchange(BluetoothSocket socket, byte[] packet, long windowMs) throws Exception {
        // Writes deliberately retain their short ACK window; only validated reads can finish early.
        return exchangeInternal(socket, packet, windowMs, false, null);
    }

    /**
     * Stream-aware read: stop after an intact state frame and a bounded quiet period.
     * Never treat a partial frame, a CRC failure or an ACK as a completed state report.
     * If the earphone doesn't answer, the original maximum timeout still applies.
     */
    private static byte[] exchange(BluetoothSocket socket, byte[] packet, long windowMs,
                                   boolean allowEarlyCompletion) throws Exception {
        return exchangeInternal(socket, packet, windowMs, allowEarlyCompletion, null);
    }

    private static byte[] exchangeInternal(BluetoothSocket socket, byte[] packet, long windowMs,
                                           boolean allowEarlyCompletion, FreeClipSetting target) throws Exception {
        InputStream in = socket.getInputStream();
        drainAvailable(in);
        OutputStream out = socket.getOutputStream();
        out.write(packet);
        out.flush();
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        long until = android.os.SystemClock.elapsedRealtime() + windowMs;
        long lastRx = -1;
        byte[] temp = new byte[4096];
        while (android.os.SystemClock.elapsedRealtime() < until) {
            int available = in.available();
            if (available > 0) {
                int count = in.read(temp, 0, Math.min(temp.length, available));
                if (count < 0) break;
                received.write(temp, 0, count);
                if (received.size() > 64 * 1024) break;
                lastRx = android.os.SystemClock.elapsedRealtime();
            } else {
                long now = android.os.SystemClock.elapsedRealtime();
                if (allowEarlyCompletion && lastRx > 0
                        && (target != null
                            ? ReadIdlePolicy.readyForSetting(target, received.toByteArray(), now-lastRx)
                            : ReadIdlePolicy.ready(packet, received.toByteArray(), now-lastRx))) break;
                Thread.sleep(20);
            }
        }
        return received.toByteArray();
    }

    private static void drainAvailable(InputStream in) throws IOException {
        byte[] tmp = new byte[512];
        int bound = 4096;
        while (bound > 0 && in.available() > 0) {
            int size = in.read(tmp, 0, Math.min(tmp.length, Math.min(in.available(), bound)));
            if (size <= 0) break;
            bound -= size;
        }
    }

    private static void close(BluetoothSocket socket) {
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }
}
