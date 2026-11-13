package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TransportRecoverySmokeTest {
    static void check(boolean ok,String label) {
        if(!ok)throw new AssertionError(label);
        System.out.println("PASS "+label);
    }
    static final class SilentSocket extends BluetoothSocket {
        final AtomicInteger writes;
        SilentSocket(AtomicInteger writes){this.writes=writes;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(new byte[0]);}
        @Override public OutputStream getOutputStream(){
            return new ByteArrayOutputStream(){
                @Override public void write(byte[] packet) {
                    if(packet.length==14) writes.incrementAndGet();
                }
            };
        }
    }
    static final class SilentDevice extends BluetoothDevice {
        final AtomicInteger writes=new AtomicInteger();
        @Override public String getAddress(){return "00:00:00:00:00:02";}
        @Override public BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid){return new SilentSocket(writes);}
    }
    static final class ConnectingSocket extends BluetoothSocket {
        final CountDownLatch entered=new CountDownLatch(1),exit=new CountDownLatch(1);
        @Override public void connect() throws IOException {
            entered.countDown();
            try { exit.await(2,TimeUnit.SECONDS); } catch(InterruptedException e){throw new IOException(e);}
            super.connect();
        }
    }
    static final class ConnectingDevice extends BluetoothDevice {
        final ConnectingSocket socket=new ConnectingSocket();
        @Override public String getAddress(){return "00:00:00:00:00:03";}
        @Override public BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid){return socket;}
    }
    public static void main(String[] args)throws Exception {
        FreeClipTransport.onBluetoothDisconnected();
        SilentDevice unknown=new SilentDevice();CountDownLatch rejected=new CountDownLatch(1);
        AtomicBoolean ok=new AtomicBoolean();AtomicInteger callbacks=new AtomicInteger();
        FreeClipTransport.write(unknown,FreeClipSetting.DROP,1,(verified,actual,error)->{
            callbacks.incrementAndGet();ok.set(!verified&&actual==null&&error!=null);rejected.countDown();
            throw new IllegalStateException("test consumer exception");
        });
        check(rejected.await(4,TimeUnit.SECONDS)&&ok.get(),"unknown protocol fails closed");
        check(unknown.writes.get()==0&&callbacks.get()==1,"no mutation or duplicate callback on unsupported firmware");
        FreeClipTransport.onBluetoothDisconnected();
        ConnectingDevice changing=new ConnectingDevice();CountDownLatch stale=new CountDownLatch(1);
        FreeClipTransport.read(changing,(states,eq,ai,error)->{ok.set(error!=null);stale.countDown();});
        check(changing.socket.entered.await(1,TimeUnit.SECONDS),"connection race entered");
        FreeClipTransport.onBluetoothDisconnected();changing.socket.exit.countDown();
        check(stale.await(3,TimeUnit.SECONDS)&&ok.get(),"disconnect invalidates in-flight connection");
        BluetoothDevice good=new BluetoothDevice();CountDownLatch recovered=new CountDownLatch(1);
        FreeClipTransport.read(good,(states,eq,ai,error)->{ok.set(error==null&&!states.isEmpty());recovered.countDown();});
        check(recovered.await(4,TimeUnit.SECONDS)&&ok.get(),"reconnect recovers on a fresh socket");
        FreeClipTransport.onBluetoothDisconnected();
        Activity activity=new Activity();
        ControlCoordinator coordinator=new ControlCoordinator(activity,good);
        CountDownLatch first=new CountDownLatch(1);
        AtomicBoolean editable=new AtomicBoolean();
        coordinator.bind(FreeClipSetting.DROP,(actual,enabled,busy)->{
            editable.set(enabled);if(actual!=null&&enabled)first.countDown();
        });
        coordinator.refresh();
        check(first.await(4,TimeUnit.SECONDS),"coordinator received editable verified state");
        coordinator.onPaused();
        check(!editable.get(),"paused panel disables stale values");
        coordinator.onResumed();
        coordinator.onDisconnected();
        check(coordinator.get(FreeClipSetting.DROP)==null&&!editable.get(),"disconnect clears cached state");
        FreeClipTransport.onBluetoothDisconnected();
    }
}
