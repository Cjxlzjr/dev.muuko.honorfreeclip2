package dev.muuko.honorfreeclip2;

import android.app.Activity;
import android.util.Log;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * Modern LSPosed API 101 entry point. No hooks in Bluetooth process or system_server.
 * All hooks use "after original", never replace an original method or a return value.
 */
public final class HonorFreeClip2Module extends XposedModule {

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!param.isFirstPackage()) return;
        String pkg = param.getPackageName();
        if ("com.hihonor.audioaccessorymanager".equals(pkg)) {
            installAudioAccessoryHook(param.getDefaultClassLoader());
        }
    }

    private void installAudioAccessoryHook(ClassLoader loader) {
        installHomePageHook(loader);
        installFunctionControlHook(loader);
    }

    private void installFunctionControlHook(ClassLoader loader) {
        try {
            Class<?> host = Class.forName(
                    "com.hihonor.earphone.functioncontrol.FunctionControlActivity", false, loader);
            Method init = host.getDeclaredMethod("c0");
            init.setAccessible(true);
            hook(init).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    if (chain.getThisObject() instanceof Activity) {
                        Activity activity = (Activity) chain.getThisObject();
                        activity.runOnUiThread(() -> {
                            try {
                                FunctionControlInjector.inject(activity);
                            } catch (Throwable err) {
                                log(Log.ERROR, ModuleConstants.TAG,
                                        "FunctionControlInjector failed", err);
                            }
                        });
                    }
                } catch (Throwable err) {
                    log(Log.ERROR, ModuleConstants.TAG, "FunctionControl hook failed", err);
                }
                return result;
            });
            log(Log.INFO, ModuleConstants.TAG, "FunctionControlActivity.c0 hook registered");
        } catch (Throwable t) {
            log(Log.ERROR, ModuleConstants.TAG, "FunctionControl hook registration failed", t);
        }
    }

    /** Home page shown in the screenshot; runs after the vendor's onResume. */
    private void installHomePageHook(ClassLoader loader) {
        try {
            Class<?> host = Class.forName(
                    "com.hihonor.earphone.homepage.EarphoneDetailsActivity", false, loader);
            Method resume = host.getDeclaredMethod("onResume");
            resume.setAccessible(true);
            hook(resume).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    if (chain.getThisObject() instanceof Activity) {
                        Activity activity = (Activity) chain.getThisObject();
                        activity.runOnUiThread(() -> {
                            try {
                                EarphoneDetailsInjector.inject(activity);
                            } catch (Throwable err) {
                                log(Log.ERROR, ModuleConstants.TAG,
                                        "EarphoneDetailsInjector failed", err);
                            }
                        });
                    }
                } catch (Throwable err) {
                    log(Log.ERROR, ModuleConstants.TAG, "EarphoneDetails hook failed", err);
                }
                return result;
            });
            log(Log.INFO, ModuleConstants.TAG,
                    "EarphoneDetailsActivity.onResume hook registered");
        } catch (Throwable t) {
            log(Log.ERROR, ModuleConstants.TAG,
                    "EarphoneDetailsActivity hook registration failed", t);
        }
    }
}
