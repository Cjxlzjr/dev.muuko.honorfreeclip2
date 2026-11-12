package android.bluetooth;
import android.os.ParcelUuid; import java.util.UUID;
public class BluetoothDevice {
  public static int createCount;
  public String getAddress(){return "AA:BB:CC:DD:EE:FF";}
  public BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid){createCount++; return new BluetoothSocket();}
  public BluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID uuid){createCount++; return new BluetoothSocket();}
  public ParcelUuid[] getUuids(){return new ParcelUuid[0];}
}
