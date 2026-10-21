package com.qmurzik.yacandy.hook;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.qmurzik.yacandy.Keys;

import java.lang.ref.WeakReference;
import java.lang.reflect.Executable;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Module entry (listed in META-INF/xposed/java_init.list). Everything here works on Android
 * framework classes only, so it does not depend on Yandex Music's obfuscated internals and keeps
 * working across app updates.
 */
public class CandyModule extends XposedModule {
    static final String TAG = "YaCandy";

    private static CandyModule instance;

    private volatile Config cfg;
    private volatile ColorEngine colors;
    private SharedPreferences prefs;
    // Strong reference: preference listeners are often held weakly.
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    private Handler main;
    private WeakReference<Activity> resumed = new WeakReference<>(null);
    private boolean started;

    private boolean colorHooks, fontHooks, scaleHooks, cornerHooks, activityHooks;

    static CandyModule get() {
        return instance;
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (started || !param.isFirstPackage() || !Keys.TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        started = true;
        instance = this;
        main = new Handler(Looper.getMainLooper());
        try {
            prefs = getRemotePreferences(Keys.GROUP);
            cfg = new Config(prefs);
            colors = new ColorEngine(cfg, systemNight());
            prefsListener = (p, key) -> scheduleReload();
            prefs.registerOnSharedPreferenceChangeListener(prefsListener);
            installHooks();
            log(Log.INFO, TAG, "Candy loaded into " + param.getPackageName());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "init failed", t);
        }
    }

    Config config() {
        return cfg;
    }

    /** Current color engine, or null when color features are off. */
    ColorEngine colors() {
        Config c = cfg;
        return c != null && c.colorsOn ? colors : null;
    }

    void logI(String msg) {
        log(Log.INFO, TAG, msg);
    }

    void logE(String msg, Throwable t) {
        log(Log.ERROR, TAG, msg, t);
    }

    void hookSafely(Executable target, XposedInterface.Hooker hooker) {
        try {
            hook(target).intercept(hooker);
        } catch (Throwable t) {
            logE("hook failed: " + target, t);
        }
    }

    // Hooks are installed lazily per feature, so a disabled feature costs nothing.
    private void installHooks() {
        Config c = cfg;
        if (!activityHooks) {
            activityHooks = true;
            ActivityHooks.install(this);
        }
        if (c.colorsOn && !colorHooks) {
            colorHooks = true;
            ColorHooks.install(this);
        }
        if (c.fontOn() && !fontHooks) {
            fontHooks = true;
            Fonts.install(this);
        }
        if (c.scaleOn() && !scaleHooks) {
            scaleHooks = true;
            ScaleHooks.install(this);
        }
        if (c.cornersOn && !cornerHooks) {
            cornerHooks = true;
            CornerHooks.install(this);
        }
    }

    private final Runnable reload = this::reloadNow;

    private void scheduleReload() {
        main.removeCallbacks(reload);
        main.postDelayed(reload, 350);
    }

    private void reloadNow() {
        try {
            Config old = cfg;
            Config c = new Config(prefs);
            cfg = c;
            colors = new ColorEngine(c, colors != null ? colors.isNight() : systemNight());
            if (old == null || old.fontVersion != c.fontVersion
                    || !old.fontSource.equals(c.fontSource) || old.fontWeightDelta != c.fontWeightDelta) {
                Fonts.reset();
            }
            installHooks();
            Activity a = resumed.get();
            if (c.liveApply && a != null && !a.isFinishing()) {
                // Compose caches its drawing, recreating is the reliable way to repaint everything.
                a.recreate();
            }
        } catch (Throwable t) {
            logE("reload failed", t);
        }
    }

    void onActivityResumed(Activity activity) {
        resumed = new WeakReference<>(activity);
        boolean night = (activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        ColorEngine e = colors;
        if (e != null && e.isNight() != night) {
            colors = new ColorEngine(cfg, night);
            // The first frame was drawn with the old theme guess.
            activity.getWindow().getDecorView().invalidate();
        }
        ViewTweaks.onResume(this, activity);
    }

    private static boolean systemNight() {
        return (android.content.res.Resources.getSystem().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
