package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.view.View;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/** After-original lifecycle hooks, confined to the audio manager process and verified pages. */
public final class HonorFreeClip2Module extends XposedModule {
    private static final String HOST_PACKAGE = "com.hihonor.audioaccessorymanager";
    private static final String HOME = "com.hihonor.earphone.homepage.EarphoneDetailsActivity";
    private static final String FUNCTION = "com.hihonor.earphone.functioncontrol.FunctionControlActivity";
    private final Set<Method> installed = new HashSet<>();
    private final WeakHashMap<Activity, Boolean> pending = new WeakHashMap<>();

    @Override public void onPackageLoaded(PackageLoadedParam param) {
        if (!param.isFirstPackage() || !HOST_PACKAGE.equals(param.getPackageName())) return;
        for (String name : new String[]{HOME, FUNCTION}) {
            try {
                Class<?> host = Class.forName(name, false, param.getDefaultClassLoader());
                for (Method method : LifecycleHookResolver.resumeMethods(host, Activity.class)) install(method);
            } catch (Throwable error) {
                CompatibilityDiagnostics.event("hook", name + " unavailable=" + error.getClass().getSimpleName());
            }
        }
        // Stable fallback also covers renamed vendor classes; runtime checks are mandatory.
        for (Method method : LifecycleHookResolver.resumeMethods(Activity.class, Activity.class)) install(method);
    }

    private void install(Method method) {
        if (installed.contains(method)) return;
        try {
            method.setAccessible(true);
            hook(method).intercept(chain -> {
                Object result = chain.proceed(); // Preserve the host result and its exceptions exactly.
                try {
                    if (chain.getThisObject() instanceof Activity) schedule((Activity) chain.getThisObject());
                } catch (Throwable error) {
                    CompatibilityDiagnostics.event("hook_callback", error.getClass().getSimpleName());
                }
                return result;
            });
            installed.add(method);
            CompatibilityDiagnostics.event("hook", method.getDeclaringClass().getName()
                    + "." + method.getName() + " installed");
        } catch (Throwable error) {
            CompatibilityDiagnostics.event("hook", method.getName() + " rejected=" + error.getClass().getSimpleName());
        }
    }

    private void schedule(Activity activity) {
        if (!HOST_PACKAGE.equals(activity.getPackageName()) || activity.isFinishing() || activity.isDestroyed()) return;
        View decor = activity.getWindow().getDecorView();
        synchronized (pending) {
            if (pending.containsKey(activity)) return;
            pending.put(activity, Boolean.TRUE);
        }
        if (!decor.post(() -> {
            synchronized (pending) { pending.remove(activity); }
            try {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                CompatibilityDiagnostics.environment(activity);
                String name = activity.getClass().getName();
                boolean home = HOME.equals(name) || HomeLayoutResolver.findUniqueScroll(activity) != null;
                boolean function = FUNCTION.equals(name) || FunctionControlInjector.hasLayoutAnchor(activity);
                if (!home && !function) {
                    CompatibilityDiagnostics.event("layout", name + " no_anchor skipped");
                    return;
                }
                if (home && function) {
                    CompatibilityDiagnostics.event("layout", "ambiguous_page skipped");
                } else if (home) EarphoneDetailsInjector.inject(activity);
                else FunctionControlInjector.inject(activity);
            } catch (Throwable error) {
                CompatibilityDiagnostics.event("injection", "skipped=" + error.getClass().getSimpleName());
            }
        })) {
            synchronized (pending) { pending.remove(activity); }
        }
    }
}
