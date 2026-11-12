package dev.muuko.honorfreeclip2;

import android.bluetooth.BluetoothDevice;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Host-only simulated earbud, not a FreeClip 2 write validation. */
public final class AiKeyTransportSmokeTest {
    static void check(boolean yes,String label) {
        if(!yes) throw new AssertionError(label);
        System.out.println("PASS "+label);
    }
    public static void main(String[] args) throws Exception {
        BluetoothDevice device=new BluetoothDevice();
        AtomicReference<AiKeyProbeCodec.State> state=new AtomicReference<>();
        CountDownLatch read=new CountDownLatch(1);
        FreeClipTransport.probeAiKey(device,(summary,observed)->{state.set(observed);read.countDown();});
        check(read.await(6,TimeUnit.SECONDS),"experimental AI query completed");
        check(AiKeyActionCodec.readable(state.get()),"simulated AI response is CRC-valid");
        check(state.get().leftRaw==0 && state.get().rightRaw==0xFF,"simulated initial AI right=none");
        int existingConnections=BluetoothDevice.createCount;
        AtomicBoolean confirmed=new AtomicBoolean(false);
        CountDownLatch written=new CountDownLatch(1);
        FreeClipTransport.writeAiKey(device,state.get(),AiKeyActionCodec.RIGHT,0,()->true,
                (ok,observed,error)->{
                    confirmed.set(ok && observed!=null && observed.rightRaw==0);
                    written.countDown();
                });
        check(written.await(8,TimeUnit.SECONDS),"AI command and readback completed");
        check(confirmed.get(),"simulated right-only write verified");
        check(BluetoothDevice.createCount==existingConnections,"AI command reused pooled socket");
        CountDownLatch stale=new CountDownLatch(1);
        AtomicBoolean allowed=new AtomicBoolean(true);
        // Same original snapshot now conflicts with actual earbud state, so no write should occur.
        FreeClipTransport.writeAiKey(device,state.get(),AiKeyActionCodec.LEFT,0xFF,()->true,
                (ok,observed,error)->{ allowed.set(ok); stale.countDown(); });
        check(stale.await(8,TimeUnit.SECONDS),"stale snapshot attempt completed");
        check(!allowed.get(),"stale AI snapshot rejected before sending");
        FreeClipTransport.onBluetoothDisconnected();
    }
}
