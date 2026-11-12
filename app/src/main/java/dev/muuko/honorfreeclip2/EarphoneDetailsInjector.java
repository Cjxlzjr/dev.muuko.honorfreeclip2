package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Attaches FreeClip 2 controls below the "更多设置" card INSIDE the earphone
 * details home page, not inside Android's Bluetooth DeviceProfilesSettings.
 *
 * Device-specific vendor binding: EarphoneDetailsActivity.A -> c1.a,
 * c1.a.j -> HwScrollView, c1.a.i -> LinearLayout.
 * Confirmed from the user's AudioAccessoryManager.apk (6 DEX files).
 */
final class EarphoneDetailsInjector {
    private static final String TAG = "honorfreeclip2-v0.3-home-panel"; // keep stable view tag during upgrades
    private static final String MORE_SETTINGS_TEXT = "更多设置";

    private EarphoneDetailsInjector() {}

    static void inject(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        DeviceMatcher.Result match = DeviceMatcher.forHomePage(activity);
        Log.i(ModuleConstants.TAG, "EarphoneDetails page device match=" + match);
        if (match != DeviceMatcher.Result.FREECLIP2) {
            removeStalePanel(activity);
            return;
        }

        // The vendor sometimes populates the "更多设置" card after onResume.
        if (!injectNow(activity)) {
            View decor = activity.getWindow().getDecorView();
            decor.postDelayed(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    try { injectNow(activity); }
                    catch (Throwable error) {
                        Log.w(ModuleConstants.TAG,
                                "EarphoneDetails delayed injection failed", error);
                    }
                }
            }, 350L);
        }
    }

    static void onPaused(Activity activity) {
        if (activity == null) return;
        try {
            Object binding = Reflection.field(activity, "A");
            View scroll = (View) Reflection.field(binding, "j");
            if (scroll != null) {
                View panel = findPanel(scroll);
                if (panel != null) SmartAudioPanel.onHomePaused(panel);
            }
        } catch (Throwable ignored) { }
    }

    private static void removeStalePanel(Activity a) {
        try {
            Object binding = Reflection.field(a, "A");
            Object scroll = Reflection.field(binding, "j");
            if (scroll instanceof View) {
                View old = findPanel((View) scroll);
                if (old != null && old.getParent() instanceof ViewGroup) {
                    ((ViewGroup) old.getParent()).removeView(old);
                }
            }
        } catch (Throwable ignored) { }
    }

    private static boolean injectNow(Activity activity) {
        Object binding;
        try {
            binding = Reflection.field(activity, "A");
        } catch (ReflectiveOperationException e) {
            Log.w(ModuleConstants.TAG, "EarphoneDetails binding field A unavailable", e);
            return false;
        }
        if (binding == null) return false;

        ViewGroup scroll;
        View preferred;
        try {
            Object j = Reflection.field(binding, "j");
            Object i = Reflection.field(binding, "i");
            scroll = j instanceof ViewGroup ? (ViewGroup) j : null;
            preferred = i instanceof View ? (View) i : null;
        } catch (ReflectiveOperationException e) {
            Log.w(ModuleConstants.TAG, "EarphoneDetails binding content fields unavailable", e);
            return false;
        }
        if (scroll == null) return false;
        // An existing panel may have survived a quick resume. Trigger a throttled
        // refresh instead of constructing a second control session.
        View existing = findPanel(scroll);
        if (existing != null) {
            SmartAudioPanel.onHomeResumed(existing);
            return true;
        }

        // The correct target must be inside the home page's scroll container
        // and must include the native "更多设置" card.
        LinearLayout container = null;
        if (preferred instanceof LinearLayout
                && ((LinearLayout) preferred).getOrientation() == LinearLayout.VERTICAL
                && isDescendant(preferred, scroll)
                && containsText(preferred, MORE_SETTINGS_TEXT)) {
            container = (LinearLayout) preferred;
        }
        if (container == null) {
            container = firstMatchingVerticalGroup(scroll);
        }
        if (container != null) {
            BluetoothDevice device = DeviceResolver.forFunction(activity);
            LinearLayout panel = FunctionControlInjector.createPanel(activity, device, false, TAG);
            // Insert BEFORE the native "更多" block. The three original cards
            // remain in their vendor-owned parent with their listeners intact,
            // and naturally appear at the bottom of the scrolled page.
            int beforeOriginalMore = findOriginalMoreStart(container);
            container.addView(panel, beforeOriginalMore,
                    new LinearLayout.LayoutParams(-1, -2));
            Log.i(ModuleConstants.TAG, "Injected V0.5 controls before native more section"
                    + " at index=" + beforeOriginalMore + "/" + container.getChildCount());
            return true;
        }

        // Last resort: wrap the ScrollView's sole existing content view. This is
        // only attempted if that content contains "更多设置". Never overlay the
        // fixed back button, picture, or battery indicators.
        if (wrapScrollContent(activity, scroll)) {
            Log.i(ModuleConstants.TAG,
                    "Injected controls into EarphoneDetailsActivity wrapped scroll content");
            return true;
        }
        Log.w(ModuleConstants.TAG,
                "EarphoneDetails has no verified scrolling insertion point; skipped safely");
        return false;
    }

    private static boolean wrapScrollContent(Activity a, ViewGroup scroll) {
        if (!(scroll instanceof ScrollView)
                && !scroll.getClass().getName().contains("ScrollView")) return false;
        if (scroll.getChildCount() != 1) return false;
        View original = scroll.getChildAt(0);
        if (original == null || !containsText(original, MORE_SETTINGS_TEXT)) return false;
        ViewGroup.LayoutParams originalParams = original.getLayoutParams();
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));
        BluetoothDevice device = DeviceResolver.forFunction(a);
        LinearLayout panel = FunctionControlInjector.createPanel(a, device, false, TAG);

        try {
            scroll.removeView(original);
            if (original instanceof LinearLayout
                    && ((LinearLayout) original).getOrientation() == LinearLayout.VERTICAL) {
                LinearLayout originalList = (LinearLayout) original;
                int beforeMore = findOriginalMoreStart(originalList);
                originalList.addView(panel, beforeMore, new LinearLayout.LayoutParams(-1, -2));
                content.addView(originalList, new LinearLayout.LayoutParams(-1, -2));
            } else {
                // Fallback for unfamiliar layouts: restore original content order.
                // Never transplant a native control out of its vendor hierarchy.
                content.addView(original, new LinearLayout.LayoutParams(-1, -2));
                content.addView(panel, new LinearLayout.LayoutParams(-1, -2));
            }
            scroll.addView(content, new ViewGroup.LayoutParams(-1, -2));
            return true;
        } catch (Throwable error) {
            // Restore the vendor UI if the custom ScrollView rejects the wrapper.
            try {
                if (panel.getParent() instanceof ViewGroup)
                    ((ViewGroup) panel.getParent()).removeView(panel);
                if (original.getParent() == content) content.removeView(original);
                if (content.getParent() == scroll) scroll.removeView(content);
                if (original.getParent() == null) scroll.addView(original, originalParams);
            } catch (Throwable restore) {
                Log.e(ModuleConstants.TAG, "EarphoneDetails rollback failed", restore);
            }
            Log.w(ModuleConstants.TAG, "EarphoneDetails wrapper rejected", error);
            return false;
        }
    }

    private static LinearLayout firstMatchingVerticalGroup(View view) {
        if (view instanceof LinearLayout) {
            LinearLayout group = (LinearLayout) view;
            if (group.getOrientation() == LinearLayout.VERTICAL
                    && containsText(group, MORE_SETTINGS_TEXT)) {
                return group; // pick outermost eligible vertical group
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                LinearLayout child = firstMatchingVerticalGroup(group.getChildAt(i));
                if (child != null) return child;
            }
        }
        return null;
    }


    /**
     * Find the beginning of the vendor-owned "更多" group without moving any
     * original buttons. This works whether the three tiles share one layout or
     * are separate direct children of the main vertical scroll content.
     */
    private static int findOriginalMoreStart(LinearLayout group) {
        int firstControl = -1;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (containsText(child, "查找耳机") || containsText(child, "功能控制")
                    || containsText(child, MORE_SETTINGS_TEXT)) {
                firstControl = i;
                break;
            }
        }
        if (firstControl == -1) return group.getChildCount();
        // Include the original section heading when it sits in its own sibling.
        for (int i = firstControl - 1; i >= Math.max(0, firstControl - 3); i--) {
            if (containsExactText(group.getChildAt(i), "更多")) return i;
        }
        return firstControl;
    }

    private static boolean containsExactText(View view, String value) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && value.equals(text.toString().trim())) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsExactText(group.getChildAt(i), value)) return true;
            }
        }
        return false;
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof TextView) {
            CharSequence label = ((TextView) view).getText();
            if (label != null && label.toString().contains(text)) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), text)) return true;
            }
        }
        return false;
    }

    private static boolean isDescendant(View child, ViewGroup ancestor) {
        for (View current = child; current != null; ) {
            if (current == ancestor) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static View findPanel(View view) {
        if (TAG.equals(view.getTag())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findPanel(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
