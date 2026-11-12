package dev.muuko.honorfreeclip2;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Simulated RFCOMM flows only; not a physical FreeClip 2 firmware test. */
public final class MultiPresetTransportSmokeTest {
    static void ok(boolean yes,String label){if(!yes)throw new AssertionError(label);System.out.println("PASS "+label);}
    static EqualizerState read(BluetoothDevice d)throws Exception{
        CountDownLatch wait=new CountDownLatch(1);
        AtomicReference<EqualizerState> result=new AtomicReference<>();
        FreeClipTransport.read(d,(values,eq,ai,error)->{result.set(eq);wait.countDown();});
        ok(wait.await(7,TimeUnit.SECONDS),"read completed");
        return result.get();
    }
    static EqualizerState create(BluetoothDevice d,EqualizerState old)throws Exception{
        CountDownLatch wait=new CountDownLatch(1);
        AtomicReference<EqualizerState> state=new AtomicReference<>();
        AtomicBoolean confirmed=new AtomicBoolean();
        FreeClipTransport.createCustomPreset(d,old,101,"轻音乐",new int[]{0,1,2,3,4,5,6,7,8,9},()->true,
                (yes,observed,error)->{confirmed.set(yes);state.set(observed);wait.countDown();});
        ok(wait.await(8,TimeUnit.SECONDS),"create completed");
        ok(confirmed.get(),"created slot readback matched");
        return state.get();
    }
    static EqualizerState select(BluetoothDevice d,EqualizerState old)throws Exception{
        CountDownLatch wait=new CountDownLatch(1);
        AtomicReference<EqualizerState> state=new AtomicReference<>();
        AtomicBoolean confirmed=new AtomicBoolean();
        FreeClipTransport.selectCustomPreset(d,old,100,()->true,
                (yes,observed,error)->{confirmed.set(yes);state.set(observed);wait.countDown();});
        ok(wait.await(8,TimeUnit.SECONDS),"select completed");
        ok(confirmed.get(),"selected slot readback matched");
        return state.get();
    }
    static EqualizerState delete(BluetoothDevice d,EqualizerState old)throws Exception{
        CountDownLatch wait=new CountDownLatch(1);
        AtomicReference<EqualizerState> state=new AtomicReference<>();
        AtomicBoolean confirmed=new AtomicBoolean();
        FreeClipTransport.deleteCustomPreset(d,old,101,()->true,
                (yes,observed,error)->{confirmed.set(yes);state.set(observed);wait.countDown();});
        ok(wait.await(8,TimeUnit.SECONDS),"delete completed");
        ok(confirmed.get(),"deleted slot absent, other slot unchanged");
        return state.get();
    }
    public static void main(String[] args)throws Exception{
        BluetoothSocket.enableMultiEq();
        BluetoothDevice d=new BluetoothDevice();
        EqualizerState original=read(d);
        ok(original!=null&&original.customCatalogKnown&&original.customPresets.size()==1,"initial CRC verified TLV08");
        EqualizerState now=create(d,original);
        ok(now.selectedId==101&&now.customPresets.size()==2,"created new 0x65 slot");
        // A previously captured catalog must be rejected when a slot was added.
        CountDownLatch stale=new CountDownLatch(1);AtomicBoolean accepted=new AtomicBoolean(true);
        FreeClipTransport.createCustomPreset(d,original,102,"旧快照",new int[10],()->true,
                (yes,observed,error)->{accepted.set(yes);stale.countDown();});
        ok(stale.await(8,TimeUnit.SECONDS),"stale candidate returned");
        ok(!accepted.get(),"stale preset list rejected before writing");
        now=select(d,now);
        ok(now.selectedId==100,"switched to original slot");
        now=delete(d,now);
        ok(now.customPresets.size()==1&&now.selectedId==100,"restored single saved slot");
        FreeClipTransport.onBluetoothDisconnected();
    }
}
