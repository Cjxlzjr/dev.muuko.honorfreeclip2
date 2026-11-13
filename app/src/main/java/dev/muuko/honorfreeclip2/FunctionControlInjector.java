package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Adds real rows directly under Honor's own RecyclerView; no Activity navigation. */
final class FunctionControlInjector {
    private static final String CONTAINER_TAG = "honorfreeclip2-v0.2-inline-root";
    private FunctionControlInjector() {}

    static boolean hasLayoutAnchor(Activity activity) {
        int id = activity.getResources().getIdentifier(
                "function_control_recycler_view", "id", activity.getPackageName());
        return id != 0 && activity.findViewById(id) != null;
    }

    static void inject(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        DeviceMatcher.Result match = DeviceMatcher.forFunctionPage(activity);
        CompatibilityDiagnostics.event("function_device", match.name());
        if (match != DeviceMatcher.Result.FREECLIP2) return;

        int recyclerId = activity.getResources().getIdentifier(
                "function_control_recycler_view", "id", activity.getPackageName());
        int scrollId = activity.getResources().getIdentifier(
                "scroll_view", "id", activity.getPackageName());
        View recycler = recyclerId == 0 ? null : activity.findViewById(recyclerId);
        View scroll = scrollId == 0 ? null : activity.findViewById(scrollId);
        LinearLayout parent = null;
        int insertAt = -1;
        if (recycler != null && recycler.getParent() instanceof LinearLayout
                && ((LinearLayout) recycler.getParent()).getOrientation() == LinearLayout.VERTICAL) {
            parent = (LinearLayout) recycler.getParent();
            insertAt = parent.indexOfChild(recycler) + 1;
        } else if (scroll instanceof ViewGroup) {
            ViewGroup container = (ViewGroup) scroll;
            if (container.getChildCount() == 1 && container.getChildAt(0) instanceof LinearLayout
                    && ((LinearLayout) container.getChildAt(0)).getOrientation() == LinearLayout.VERTICAL
                    && recycler != null) {
                parent = (LinearLayout) container.getChildAt(0);
                insertAt = parent.getChildCount();
            }
        }
        if (parent == null) {
            CompatibilityDiagnostics.event("function_layout", "no_safe_parent skipped");
            return;
        }
        for (int i = 0; i < parent.getChildCount(); i++)
            if (CONTAINER_TAG.equals(parent.getChildAt(i).getTag())) {
                SmartAudioPanel.onHomeResumed(parent.getChildAt(i));
                CompatibilityDiagnostics.event("function_layout", "existing_panel reused");
                return;
            }
        // Old v0.1.2 preview card must not remain if LSPosed rehooks without recreating Activity.
        for (int i = parent.getChildCount() - 1; i >= 0; i--)
            if ("honorfreeclip2-v0.1.2-card".equals(parent.getChildAt(i).getTag()))
                parent.removeViewAt(i);
        BluetoothDevice device = DeviceResolver.forFunction(activity);
        LinearLayout wrapper = createPanel(activity, device, true, CONTAINER_TAG);
        parent.addView(wrapper, Math.min(Math.max(insertAt, 0), parent.getChildCount()),
                new LinearLayout.LayoutParams(-1, -2));
        CompatibilityDiagnostics.event("function_layout", "matched inserted");
        Log.i(ModuleConstants.TAG, "Injected inline controls into FunctionControlActivity (v0.4 style)");
    }

    /** Both Honor native pages reuse the same Huawei SmartAudio-style inline panel. */
    static LinearLayout createPanel(Activity activity, BluetoothDevice device,
                                    boolean originalWearToggleExists, String tag) {
        return SmartAudioPanel.create(activity, device, originalWearToggleExists, tag);
    }
}
