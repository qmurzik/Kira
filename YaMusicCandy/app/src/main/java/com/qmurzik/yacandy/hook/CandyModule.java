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
 * Module entry (listed in META-INF/xposed/java_init.list). Appearance hooks work on Android
 * framework classes only; app features hook either names the app keeps (Gson DTOs, Retrofit APIs,
 * AppMetrica) or code found by string constants ({@link Finder}), so app updates don't break them.
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
    private boolean feedHooks, apiHooks, telemetryHooks, pushHooks, animHooks;
    private volatile boolean experimentHooks;
    private ClassLoader appLoader;
    private android.content.pm.ApplicationInfo appInfo;

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
        appLoader = param.getClassLoader();
        appInfo = param.getApplicationInfo();
        try {
            prefs = getRemotePreferences(Keys.GROUP);
            cfg = new Config(prefs);
            colors = new ColorEngine(cfg, systemNight());
            prefsListener = (p, key) -> scheduleReload();
            prefs.registerOnSharedPreferenceChangeListener(prefsListener);
            installHooks(true);
            log(Log.INFO, TAG, "Candy loaded into " + param.getPackageName());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "init failed", t);
        }
    }

    Config config() {
        return cfg;
    }

    String appDataDir() {
        return appInfo.dataDir;
    }

    ClassLoader appLoader() {
        return appLoader;
    }

    private Finder finder;

    /** Dex index of the running app build, loaded on first use (cached per build on disk). */
    synchronized Finder finder() {
        if (finder == null) finder = Finder.load(this, appInfo, appLoader);
        return finder;
    }

    private final java.util.Map<String, String> statuses =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());

    /** Records what a feature found in this app build; shown on the module's compatibility page. */
    void status(String feature, String detail) {
        statuses.put(feature, detail);
    }

    /** "feature\tdetail" lines for the settings app. */
    String statusReport() {
        StringBuilder sb = new StringBuilder();
        synchronized (statuses) {
            for (java.util.Map.Entry<String, String> e : statuses.entrySet()) {
                sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            }
        }
        return sb.toString();
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

    /** For hookers that throw on purpose (e.g. failing a DNS lookup). */
    void hookPassthrough(Executable target, XposedInterface.Hooker hooker) {
        try {
            hook(target).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept(hooker);
        } catch (Throwable t) {
            logE("hook failed: " + target, t);
        }
    }

    // Hooks are installed lazily per feature, so a disabled feature costs nothing. Most app-feature
    // hooks only see objects created after them, which is why those settings ask for a restart.
    private void installHooks(boolean atStart) {
        Config c = cfg;
        if (c.feedOn() && !feedHooks) {
            feedHooks = true;
            FeedHooks.install(this, appLoader);
        }
        if (!c.blockedApis.isEmpty() && !apiHooks) {
            apiHooks = true;
            NetHooks.installApiBlocker(this);
        }
        if (c.telemetryOff && !telemetryHooks) {
            telemetryHooks = true;
            TelemetryHooks.install(this, appLoader);
            NetHooks.installTrackerBlocker(this);
        }
        if (c.promoPushOff && !pushHooks) {
            pushHooks = true;
            SystemHooks.installPushFilter(this);
        }
        if (!Float.isNaN(c.animScale) && !animHooks) {
            animHooks = true;
            SystemHooks.installAnimationScale(this);
        }
        if (c.experimentsOn() && !experimentHooks) {
            experimentHooks = true;
            Thread scan = new Thread(() -> ExperimentHooks.install(this, finder()),
                    "YaCandy-scan");
            scan.start();
            if (atStart) {
                // Experiments are read during start-up, so give the hooks a head start. Only the first
                // launch after an app update scans the dex; later launches read the cache instantly.
                try {
                    scan.join(4000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
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
            if (old == null || old.fontVersion != c.fontVersion || old.fontBoldVersion != c.fontBoldVersion
                    || !old.fontSource.equals(c.fontSource) || !old.fontBoldSource.equals(c.fontBoldSource)
                    || old.fontWeightDelta != c.fontWeightDelta) {
                Fonts.reset();
            }
            installHooks(false);
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
        SettingsEntry.onResume(this, activity);
        Config c = cfg;
        if (c != null) SystemHooks.onResume(c, activity);
    }

    private static boolean systemNight() {
        return (android.content.res.Resources.getSystem().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
