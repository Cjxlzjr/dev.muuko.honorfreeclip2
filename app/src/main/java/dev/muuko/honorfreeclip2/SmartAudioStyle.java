package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Self-contained styling: never changes the vendor Activity theme or global resources. */
final class SmartAudioStyle {
    static final int TEXT = Color.rgb(27, 29, 34);
    static final int SECONDARY = Color.rgb(119, 123, 132);
    static final int SUBTLE = Color.rgb(153, 157, 165);
    static final int BLUE = Color.rgb(31, 113, 241);
    static final int BLUE_PALE = Color.rgb(231, 239, 255);
    static final int SURFACE = Color.WHITE;
    static final int SEGMENT = Color.rgb(245, 246, 249);
    static final int LINE = Color.rgb(237, 239, 243);

    private SmartAudioStyle() { }

    static int dp(Activity activity, float size) {
        return Math.round(activity.getResources().getDisplayMetrics().density * size);
    }

    static TextView text(Activity a, String value, int sp, int color, boolean bold) {
        TextView v = new TextView(a);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    static GradientDrawable rounded(Activity a, int fill, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(a, radiusDp));
        return drawable;
    }

    static LinearLayout vertical(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout horizontal(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static void title(Activity a, LinearLayout parent, String label) {
        TextView t = text(a, label, 15, SECONDARY, false);
        t.setPadding(dp(a, 8), dp(a, 15), 0, dp(a, 9));
        parent.addView(t, new LinearLayout.LayoutParams(-1, dp(a, 48)));
    }

    static LinearLayout card(Activity a, LinearLayout parent) {
        LinearLayout c = vertical(a);
        c.setBackground(rounded(a, SURFACE, 22));
        c.setPadding(dp(a, 16), dp(a, 15), dp(a, 16), dp(a, 15));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(a, 11);
        parent.addView(c, p);
        return c;
    }

    static View divider(Activity a) {
        View v = new View(a);
        v.setBackgroundColor(LINE);
        return v;
    }

    static Switch toggle(Activity a) {
        Switch sw = new Switch(a);
        sw.setShowText(false);
        // Honor stock toggles are blue; keep the stock Switch geometry and touch behavior.
        sw.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{BLUE, Color.rgb(214, 217, 223)}));
        sw.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        sw.setEnabled(false);
        return sw;
    }

    static void show(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
