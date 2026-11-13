package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CompatibilityLayoutTest {
    public static class RenamedActivity extends Activity { }
    private Activity activity() { return Robolectric.buildActivity(RenamedActivity.class).setup().get(); }
    private ScrollView scroll(Activity activity,String marker) {
        ScrollView scroll=new ScrollView(activity);
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(activity);title.setText(marker);content.addView(title);scroll.addView(content);
        return scroll;
    }
    private boolean inject(Activity activity) throws Exception {
        Method method=EarphoneDetailsInjector.class.getDeclaredMethod("injectNow",Activity.class);
        method.setAccessible(true);return (Boolean)method.invoke(null,activity);
    }
    @Test public void renamedBindingFindsVerifiedContainerAndDoesNotDuplicate() throws Exception {
        Activity activity=activity();
        ScrollView scroll=scroll(activity,"更多设置");
        LinearLayout content=(LinearLayout)scroll.getChildAt(0);
        View original=content.getChildAt(0);
        AtomicInteger clicks=new AtomicInteger();original.setOnClickListener(v->clicks.incrementAndGet());
        activity.setContentView(scroll);
        assertSame(scroll,HomeLayoutResolver.findUniqueScroll(activity));
        assertTrue(inject(activity));assertEquals(2,content.getChildCount());
        assertSame(original,content.getChildAt(1));
        assertTrue(inject(activity));assertEquals(2,content.getChildCount());
        original.performClick();assertEquals(1,clicks.get());
    }
    @Test public void unknownAndAmbiguousLayoutsRemainUntouched() throws Exception {
        Activity activity=activity();
        ScrollView unknown=scroll(activity,"Unknown");
        activity.setContentView(unknown);
        assertNull(HomeLayoutResolver.findUniqueScroll(activity));
        assertFalse(inject(activity));assertEquals(1,unknown.getChildCount());
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.addView(scroll(activity,"更多设置"));root.addView(scroll(activity,"更多设置"));
        activity.setContentView(root);
        assertNull(HomeLayoutResolver.findUniqueScroll(activity));assertFalse(inject(activity));
        assertEquals(2,root.getChildCount());
    }
    @Test public void moduleTextCannotBecomeAVendorAnchor() {
        Activity activity=activity();ScrollView scroll=scroll(activity,"更多设置");
        scroll.getChildAt(0).setTag("honorfreeclip2-test");
        activity.setContentView(scroll);
        assertNull(HomeLayoutResolver.findUniqueScroll(activity));
    }
    @Test public void functionPageWithoutResourceAnchorIsSkipped() {
        Activity activity=activity();
        assertFalse(FunctionControlInjector.hasLayoutAnchor(activity));
        assertEquals(2,LifecycleHookResolver.resumeMethods(RenamedActivity.class,Activity.class).size());
    }
}
