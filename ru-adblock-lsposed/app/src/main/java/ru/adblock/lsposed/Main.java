package ru.adblock.lsposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Точка входа модуля (assets/xposed_init). */
public class Main implements IXposedHookZygoteInit, IXposedHookLoadPackage {
    private static String modulePath;
    private static boolean installed;

    @Override
    public void initZygote(StartupParam startupParam) {
        modulePath = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (!lpparam.isFirstApplication) return;
            String pkg = lpparam.packageName;
            String proc = lpparam.processName;
            if (BuildConfig.APPLICATION_ID.equals(pkg) || "android".equals(pkg)) return;
            if (Exclusions.isExcluded(pkg, proc)) {
                L.i("пропущен банковский/платёжный процесс: " + proc);
                return;
            }
            synchronized (Main.class) {
                if (installed) return;
                installed = true;
            }

            Config cfg = Config.load();
            L.debug = cfg.debug;
            Rules rules = Rules.load(modulePath, cfg);
            ClassLoader cl = lpparam.classLoader;
            L.i("активен в " + proc + " [sdk=" + cfg.sdk + ", dns=" + cfg.dns + ", json=" + cfg.json
                    + ", ui=" + cfg.ui + "]");

            if (cfg.sdk) {
                try {
                    SdkHooks.install(cl);
                } catch (Throwable t) {
                    L.e("слой SDK", t);
                }
            }
            if (cfg.dns) {
                try {
                    DnsHooks.install(cl, rules);
                } catch (Throwable t) {
                    L.e("слой DNS", t);
                }
            }
            if (cfg.json) {
                try {
                    JsonFilter.init(rules);
                    JsonFilter.installOrgJson();
                    OkHttpHooks.installJson(cl);
                } catch (Throwable t) {
                    L.e("слой JSON", t);
                }
            }
            if (cfg.ui) {
                try {
                    UiHooks.install(cl);
                } catch (Throwable t) {
                    L.e("слой UI", t);
                }
            }
        } catch (Throwable t) {
            L.e("handleLoadPackage", t);
        }
    }
}
