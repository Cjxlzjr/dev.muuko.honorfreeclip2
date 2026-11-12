package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Reusable no-title bottom sheet. Keeps existing settings Views/bindings alive across opens. */
final class SettingsSheet {
    interface Choice { void selected(int index); }
    private static WeakReference<Dialog> current = new WeakReference<>(null);
    private static Runnable restoreCurrent;

    private SettingsSheet() { }

    static Dialog show(Activity a, String title, View detail) {
        if (a == null || a.isFinishing() || a.isDestroyed() || detail == null) return null;
        // Never stack two setting dialogs: close the old one and restore its bound View first.
        Dialog old = current.get();
        if (old != null && old.isShowing()) {
            old.dismiss();
            // Android can deliver OnDismiss asynchronously. Restore synchronously before
            // moving the same bound View into a new dialog (rapid taps / nested info page).
            if (restoreCurrent != null) restoreCurrent.run();
        }
        ViewParent parent = detail.getParent();
        if (!(parent instanceof ViewGroup)) return null;
        ViewGroup origin = (ViewGroup) parent;
        ViewGroup.LayoutParams previousParams = detail.getLayoutParams();
        Dialog dialog = new Dialog(a);
        // MagicOS may draw a cyan title divider unless NO_TITLE is requested here.
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        int radius = dp(a, 31);
        LinearLayout sheet = SmartAudioStyle.vertical(a);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(246, 247, 250));
        bg.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 20));

        LinearLayout handleRow = SmartAudioStyle.horizontal(a);
        handleRow.setGravity(Gravity.CENTER);
        View handle = new View(a);
        handle.setBackground(SmartAudioStyle.rounded(a, Color.rgb(192,194,200), 4));
        handleRow.addView(handle, new LinearLayout.LayoutParams(dp(a, 42), dp(a, 5)));
        sheet.addView(handleRow, new LinearLayout.LayoutParams(-1, dp(a, 24)));

        LinearLayout top = SmartAudioStyle.horizontal(a);
        TextView heading = SmartAudioStyle.text(a, title, 22, SmartAudioStyle.TEXT, true);
        top.addView(heading, new LinearLayout.LayoutParams(0, dp(a, 62), 1));
        TextView close = SmartAudioStyle.text(a, "×", 29, SmartAudioStyle.SECONDARY, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(SmartAudioStyle.rounded(a, Color.rgb(230,232,237), 24));
        close.setContentDescription("关闭" + title);
        close.setOnClickListener(v -> dialog.dismiss());
        top.addView(close, new LinearLayout.LayoutParams(dp(a, 39), dp(a, 39)));
        sheet.addView(top);

        int maxScroll = Math.max(dp(a, 240), Math.min(dp(a, 650),
                a.getResources().getDisplayMetrics().heightPixels - dp(a, 190)));
        LimitedScrollView scroll = new LimitedScrollView(a, maxScroll);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        origin.removeView(detail);
        detail.setVisibility(View.VISIBLE);
        scroll.addView(detail, new FrameLayout.LayoutParams(-1, -2));
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.getDecorView().setBackgroundColor(Color.TRANSPARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.32f);
            window.setGravity(Gravity.BOTTOM);
        }
        final boolean[] restored = {false};
        Runnable restore = () -> {
            if (restored[0]) return;
            restored[0] = true;
            ViewParent p = detail.getParent();
            if (p instanceof ViewGroup) ((ViewGroup) p).removeView(detail);
            detail.setVisibility(View.GONE);
            if (previousParams != null) origin.addView(detail, previousParams);
            else origin.addView(detail);
        };
        dialog.setOnDismissListener(d -> {
            restore.run();
            if (current.get() == dialog) {
                current.clear();
                restoreCurrent = null;
            }
        });
        current = new WeakReference<>(dialog);
        restoreCurrent = restore;
        try {
            dialog.show();
            if (window != null) window.setLayout(-1, -2);
        } catch (RuntimeException error) {
            // A vendor WindowManager failure must not leave the bound card detached.
            android.util.Log.w(ModuleConstants.TAG, "Settings sheet could not open: " + title, error);
            restore.run();
            current.clear();
            restoreCurrent = null;
            return null;
        }
        return dialog;
    }

    static void showChoices(Activity a, String title, String subtitle,
                            String[] options, int selected, Choice choice) {
        if (options == null || options.length == 0) return;
        LinearLayout staging = SmartAudioStyle.vertical(a);
        LinearLayout card = SmartAudioStyle.card(a, staging);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView hint = SmartAudioStyle.text(a, subtitle, 12, SmartAudioStyle.SECONDARY, false);
            hint.setPadding(0, 0, 0, dp(a, 13));
            card.addView(hint);
        }
        final Dialog[] ref = new Dialog[1];
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            LinearLayout row = SmartAudioStyle.horizontal(a);
            row.setMinimumHeight(dp(a, 54));
            TextView label = SmartAudioStyle.text(a, options[i], 15,
                    index == selected ? SmartAudioStyle.BLUE : SmartAudioStyle.TEXT,
                    index == selected);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            TextView mark = SmartAudioStyle.text(a, index == selected ? "✓" : "›", 19,
                    index == selected ? SmartAudioStyle.BLUE : SmartAudioStyle.SUBTLE, false);
            mark.setGravity(Gravity.CENTER);
            row.addView(mark, new LinearLayout.LayoutParams(dp(a, 28), -1));
            row.setClickable(true);
            row.setContentDescription(options[i] + (index == selected ? "，当前选中" : ""));
            row.setOnClickListener(v -> {
                if (ref[0] != null) ref[0].dismiss();
                choice.selected(index);
            });
            card.addView(row);
            if (i + 1 < options.length) {
                View line = new View(a);
                line.setBackgroundColor(SmartAudioStyle.LINE);
                card.addView(line, new LinearLayout.LayoutParams(-1, dp(a, 1)));
            }
        }
        ref[0] = show(a, title, card);
    }

    static void showInfo(Activity a, String title, String message) {
        LinearLayout staging = SmartAudioStyle.vertical(a);
        LinearLayout card = SmartAudioStyle.card(a, staging);
        TextView body = SmartAudioStyle.text(a, message, 14, SmartAudioStyle.SECONDARY, false);
        body.setPadding(0, dp(a, 4), 0, dp(a, 9));
        card.addView(body);
        show(a, title, card);
    }

    private static int dp(Activity a, int dp) { return SmartAudioStyle.dp(a, dp); }

    private static final class LimitedScrollView extends ScrollView {
        final int maxHeight;
        LimitedScrollView(Activity a, int maxHeight) {
            super(a); this.maxHeight = maxHeight;
        }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int limit = Math.min(maxHeight, View.MeasureSpec.getSize(heightMeasureSpec));
            // UNSPECIFIED has size 0: keep the explicit device-sized cap instead.
            if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED)
                limit = maxHeight;
            super.onMeasure(widthMeasureSpec,
                    View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST));
        }
    }
}
