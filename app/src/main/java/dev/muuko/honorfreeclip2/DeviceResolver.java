package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import java.util.Set;

/** Never guesses the earphone address: the selected device must be resolved from the host. */
final class DeviceResolver {
    private DeviceResolver() {}

    static BluetoothDevice forFunction(Activity activity) {
        try {
            Object addr = Reflection.field(activity, "y");
            if (addr instanceof String) {
                BluetoothDevice device = fromAddress((String) addr);
                if (device != null) return device;
            }
        } catch (Throwable ignored) { }
        BluetoothDevice explicit = fromIntent(activity.getIntent());
        return explicit != null ? explicit : uniqueBondedFreeClip2();
    }

    static BluetoothDevice forSettings(Object fragment) {
        try {
            Object cached = Reflection.field(fragment, "a");
            Object device = Reflection.callNoArgs(cached, "e0");
            if (device instanceof BluetoothDevice) return (BluetoothDevice) device;
        } catch (Throwable ignored) { }
        try {
            Object args = Reflection.callNoArgs(fragment, "getArguments");
            if (args instanceof Bundle) return fromBundle((Bundle) args);
        } catch (Throwable ignored) { }
        return null;
    }

    private static BluetoothDevice fromIntent(Intent intent) {
        return intent == null ? null : fromBundle(intent.getExtras());
    }

    @SuppressWarnings("deprecation")
    private static BluetoothDevice fromBundle(Bundle bundle) {
        if (bundle == null) return null;
        try {
            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                if (value instanceof BluetoothDevice) return (BluetoothDevice) value;
                if (value instanceof String) {
                    BluetoothDevice device = fromAddress((String) value);
                    if (device != null) return device;
                }
            }
        } catch (Throwable t) { Log.w(ModuleConstants.TAG, "Unable to extract device from bundle", t); }
        return null;
    }

    /** Only fall back to a uniquely paired FreeClip 2; never choose arbitrarily. */
    private static BluetoothDevice uniqueBondedFreeClip2() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return null;
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            BluetoothDevice match = null;
            if (bonded == null) return null;
            for (BluetoothDevice device : bonded) {
                String name = device.getName();
                if (name == null || !name.toLowerCase(java.util.Locale.ROOT)
                        .replace(" ", "").replace("_", "").contains("freeclip2")) continue;
                if (match != null) return null;
                match = device;
            }
            return match;
        } catch (SecurityException denied) { return null; }
        catch (Throwable ignored) { return null; }
    }

    private static BluetoothDevice fromAddress(String address) {
        try {
            if (!BluetoothAdapter.checkBluetoothAddress(address)) return null;
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            return adapter == null ? null : adapter.getRemoteDevice(address);
        } catch (Throwable ignored) { return null; }
    }
}
