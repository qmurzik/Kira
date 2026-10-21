package com.qmurzik.yacandy.hook;

import android.app.Activity;

final class ActivityHooks {
    private ActivityHooks() {
    }

    static void install(CandyModule m) {
        try {
            m.hookSafely(Activity.class.getDeclaredMethod("onResume"), chain -> {
                Object result = chain.proceed();
                try {
                    m.onActivityResumed((Activity) chain.getThisObject());
                } catch (Throwable t) {
                    m.logE("onResume", t);
                }
                return result;
            });
        } catch (Throwable t) {
            m.logE("activity hooks", t);
        }
    }
}
