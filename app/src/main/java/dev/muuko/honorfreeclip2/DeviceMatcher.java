package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Locale;

/**
 * Read-only detection. No spoofed device identity and no changes to Bluetooth services.
 * Settings: q0 field a (cached device) / q0.e0() -> BluetoothDevice verified in supplied APK.
 * Function control: superclass BaseActivity.y / Z() may hold the Bluetooth device address.
 */
final class DeviceMatcher {
    enum Result { FREECLIP2, OTHER, UNKNOWN }

    private DeviceMatcher() {}

    static Result forFunctionPage(Activity activity) {
        try {
            Result r = fromAddressOrName((String) Reflection.field(activity, "y"));
            if (r != Result.UNKNOWN) return r;
        } catch (Throwable ignored) { }
        // FunctionControlActivity.Z() returns the constant log tag
        // "FunctionControlActivity", NOT a Bluetooth address (confirmed in DEX).
        // BaseActivity.y is the actual String field to inspect.
        return fromIntent(activity.getIntent());
    }

    /** The home page uses the same BaseActivity.y selected-address field. */
    static Result forHomePage(Activity activity) {
        Result selected = forFunctionPage(activity);
        if (selected != Result.UNKNOWN) return selected;
        View root = activity.findViewById(android.R.id.content);
        // A visible FreeClip 2 product name on the active detail page is useful when
        // the vendor hides the Bluetooth address behind a custom parcelable.
        return containsName(root, "freeclip2") ? Result.FREECLIP2 : Result.UNKNOWN;
    }

    private static boolean containsName(View root, String needle) {
        Object tag = root == null ? null : root.getTag();
        if (tag instanceof String && ((String) tag).startsWith("honorfreeclip2-")) return false;
        if (root instanceof TextView) {
            CharSequence value = ((TextView) root).getText();
            if (value != null) {
                String normalized = value.toString().toLowerCase(Locale.ROOT)
                        .replace(" ", "").replace("-", "").replace("_", "");
                if (normalized.contains(needle)) return true;
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsName(group.getChildAt(i), needle)) return true;
            }
        }
        return false;
    }

    static Result forSettingsPage(Object fragment) {
        try {
            Object cached = Reflection.field(fragment, "a");
            Object device = Reflection.callNoArgs(cached, "e0");
            if (device instanceof BluetoothDevice) {
                Result r = fromBluetoothDevice((BluetoothDevice) device);
                if (r != Result.UNKNOWN) return r;
            }
        } catch (Throwable ignored) { }
        try {
            Object args = Reflection.callNoArgs(fragment, "getArguments");
            if (args instanceof Bundle) return fromBundle((Bundle) args);
        } catch (Throwable ignored) { }
        return Result.UNKNOWN;
    }

    private static Result fromIntent(Intent intent) {
        try {
            return intent == null ? Result.UNKNOWN : fromBundle(intent.getExtras());
        } catch (Throwable ignored) { return Result.UNKNOWN; }
    }

    private static Result fromBundle(Bundle bundle) {
        if (bundle == null) return Result.UNKNOWN;
        try {
            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                if (value instanceof String) {
                    Result r = fromAddressOrName((String) value);
                    if (r != Result.UNKNOWN) return r;
                } else if (value instanceof BluetoothDevice) {
                    Result r = fromBluetoothDevice((BluetoothDevice) value);
                    if (r != Result.UNKNOWN) return r;
                }
            }
        } catch (Throwable ignored) { }
        return Result.UNKNOWN;
    }

    private static Result fromBluetoothDevice(BluetoothDevice device) {
        try {
            String name = device.getName();
            // Prevent a recursive lookup when a vendor returns an address as the name.
            if (name == null || BluetoothAdapter.checkBluetoothAddress(name)) return Result.UNKNOWN;
            return fromAddressOrName(name);
        } catch (Throwable ignored) { return Result.UNKNOWN; }
    }

    private static Result fromAddressOrName(String value) {
        if (value == null || value.trim().isEmpty()) return Result.UNKNOWN;
        String normalized = value.toLowerCase(Locale.ROOT).replace(" ", "")
                .replace("_", "").replace("-", "");
        if (normalized.contains("freeclip2") || normalized.contains("00q5r1"))
            return Result.FREECLIP2;
        if (BluetoothAdapter.checkBluetoothAddress(value)) {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    BluetoothDevice device = adapter.getRemoteDevice(value);
                    Result r = fromBluetoothDevice(device);
                    if (r != Result.UNKNOWN) return r;
                }
            } catch (Throwable err) {
                Log.d(ModuleConstants.TAG, "Bluetooth device name lookup unavailable");
            }
            return Result.UNKNOWN;
        }
        // Only identifiable earphone names are treated as definitely different devices.
        if (normalized.contains("freebuds") || normalized.contains("freeclip")
                || normalized.contains("freearc")) return Result.OTHER;
        return Result.UNKNOWN;
    }
}
