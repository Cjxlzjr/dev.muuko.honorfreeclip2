package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Bounded, read-only fallback when generated binding fields have been renamed. */
final class HomeLayoutResolver {
    private HomeLayoutResolver() { }
    static ViewGroup findUniqueScroll(Activity activity) {
        List<ViewGroup> matches = new ArrayList<>();
        int[] budget = {2048};
        scan(activity.findViewById(android.R.id.content), 0, budget, matches);
        return budget[0] > 0 && matches.size() == 1 ? matches.get(0) : null;
    }
    private static void scan(View view, int depth, int[] budget, List<ViewGroup> matches) {
        if (view == null || --budget[0] <= 0 || matches.size() > 1) return;
        if (depth > 40) { budget[0] = 0; return; }
        if (isModule(view)) return;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (view.getClass().getSimpleName().contains("ScrollView")
                    && containsMore(group, 0, new int[]{1024})) matches.add(group);
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i), depth + 1, budget, matches);
        }
    }
    private static boolean containsMore(View view, int depth, int[] budget) {
        if (view == null || depth > 40 || --budget[0] <= 0 || isModule(view)) return false;
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && "更多设置".equals(text.toString().trim())) return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (containsMore(group.getChildAt(i), depth + 1, budget)) return true;
        }
        return false;
    }
    private static boolean isModule(View view) {
        return view.getTag() instanceof String && ((String) view.getTag()).startsWith("honorfreeclip2-");
    }
}
