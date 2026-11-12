package dev.muuko.honorfreeclip2;

import android.bluetooth.BluetoothDevice;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test transport worker, batch decode, target write readback and connection reuse with a fake socket. */
public final class TransportPoolSmokeTest {
    static void check(boolean ok, String label) { if(!ok)throw new AssertionError(label);System.out.println("PASS "+label); }
    public static void main(String[] args) throws Exception {
        BluetoothDevice device=new BluetoothDevice();
        CountDownLatch read=new CountDownLatch(1);
        AtomicBoolean ok=new AtomicBoolean();
        FreeClipTransport.read(device,(vals,eq,ai,err)->{
            ok.set(err==null && eq!=null && vals.containsKey(FreeClipSetting.WEAR)
                    && vals.containsKey(FreeClipSetting.SPATIAL_MODE)
                    && vals.containsKey(FreeClipSetting.DOUBLE_LEFT));
            read.countDown();
        });
        check(read.await(8,TimeUnit.SECONDS),"combined read completed");
        check(ok.get(),"combined response was decoded");
        int count=BluetoothDevice.createCount;
        check(count==1,"first operation opened one socket");
        CountDownLatch written=new CountDownLatch(1);
        FreeClipTransport.write(device,FreeClipSetting.DROP,1,(verified,actual,error)->{
            ok.set(verified && actual!=null && actual==1);
            written.countDown();
        });
        check(written.await(7,TimeUnit.SECONDS),"targeted write completed");
        check(ok.get(),"write was CRC-verified against target setting");
        check(BluetoothDevice.createCount==count,"write reused cached socket");
        FreeClipTransport.onPagePaused();
        CountDownLatch reread=new CountDownLatch(1);
        FreeClipTransport.read(device,(vals,eq,ai,err)->reread.countDown());
        check(reread.await(8,TimeUnit.SECONDS),"read after pause completed");
        check(BluetoothDevice.createCount==count+1,"page pause forced new socket");
        FreeClipTransport.onBluetoothDisconnected();
    }
}
