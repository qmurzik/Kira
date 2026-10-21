package com.qmurzik.yacandy.hook;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

/** Scales the whole UI (density) and text (fontScale) by handing activities a re-configured context. */
final class ScaleHooks {
    private ScaleHooks() {
    }

    static void install(CandyModule m) {
        try {
            m.hookSafely(Activity.class.getDeclaredMethod("attachBaseContext", Context.class), chain -> {
                Config c = m.config();
                Context base = (Context) chain.getArg(0);
                if (c == null || base == null || !c.scaleOn()) return chain.proceed();
                Configuration conf = new Configuration(base.getResources().getConfiguration());
                if (Math.abs(c.uiScale - 1f) > 0.001f) {
                    conf.densityDpi = Math.max(80, Math.round(conf.densityDpi * c.uiScale));
                }
                conf.fontScale = conf.fontScale * c.fontScale;
                return chain.proceed(new Object[]{base.createConfigurationContext(conf)});
            });
        } catch (Throwable t) {
            m.logE("scale hooks", t);
        }
    }
}
