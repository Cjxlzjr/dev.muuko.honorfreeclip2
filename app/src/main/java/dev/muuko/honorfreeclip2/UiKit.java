package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Small native UI without AndroidX dependencies, matching a light MagicOS-style card layout. */
final class UiKit {
    static final int BACKGROUND = Color.rgb(246, 247, 250);
    static final int PRIMARY = Color.rgb(28, 29, 32);
    static final int MUTED = Color.rgb(111, 115, 124);
    static final int BLUE = Color.rgb(24, 112, 243);
    private UiKit() { }

    static int dp(Activity a, float px) {
        return Math.round(px * a.getResources().getDisplayMetrics().density);
    }

    static LinearLayout createPage(Activity a, String title, String subtitle) {
        a.getWindow().setStatusBarColor(BACKGROUND);
        a.getWindow().setNavigationBarColor(BACKGROUND);
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a, 20), dp(a, 20), dp(a, 20), dp(a, 28));
        scroll.addView(root);
        a.setContentView(scroll);
        TextView header = text(a, title, 29, PRIMARY);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(header);
        if (subtitle != null) {
            TextView hint = text(a, subtitle, 13, MUTED);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.topMargin = dp(a, 6); p.bottomMargin = dp(a, 18);
            root.addView(hint, p);
        }
        return root;
    }

    static TextView text(Activity a, String str, float sp, int color) {
        TextView v = new TextView(a);
        v.setText(str);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    static LinearLayout card(Activity a, LinearLayout parent) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 18), dp(a, 16), dp(a, 18), dp(a, 16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(a, 20));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(a, 12);
        parent.addView(card, lp);
        return card;
    }

    static void addSection(Activity a, LinearLayout root, String title, String description) {
        LinearLayout card = card(a, root);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(a, title, 17, PRIMARY);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView badge = text(a, "待接入", 12, MUTED);
        row.addView(badge);
        card.addView(row);
        TextView summary = text(a, description, 13, MUTED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(a, 8);
        card.addView(summary, lp);
    }
}
