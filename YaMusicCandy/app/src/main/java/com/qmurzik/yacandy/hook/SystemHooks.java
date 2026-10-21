package com.qmurzik.yacandy.hook;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.os.Build;
import android.provider.Settings;
import android.view.WindowManager;

import com.qmurzik.yacandy.Features;

import java.lang.reflect.Method;

/** Framework-level tweaks: promo notifications, animation speed, keeping the screen on. */
final class SystemHooks {
    private SystemHooks() {
    }

    /** Drops notifications posted to the marketing push channels; player/download ones stay. */
    static void installPushFilter(CandyModule m) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            m.hookSafely(NotificationManager.class.getDeclaredMethod("notify",
                    String.class, int.class, Notification.class), chain -> {
                Config c = m.config();
                Notification n = (Notification) chain.getArg(2);
                if (c != null && c.promoPushOff && n != null && isPromo(n.getChannelId())) {
                    m.logI("promo notification dropped (channel " + n.getChannelId() + ")");
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            m.logE("push filter", t);
        }
    }

    private static boolean isPromo(String channel) {
        if (channel == null) return false;
        for (String c : Features.PROMO_PUSH_CHANNELS) if (c.equals(channel)) return true;
        return false;
    }

    /**
     * Views animate through ValueAnimator; Compose reads the same system setting via
     * Settings.Global, so both are answered with the chosen scale.
     */
    static void installAnimationScale(CandyModule m) {
        try {
            m.hookSafely(ValueAnimator.class.getDeclaredMethod("getDurationScale"), chain -> {
                Config c = m.config();
                return c != null && !Float.isNaN(c.animScale) ? c.animScale : chain.proceed();
            });
        } catch (Throwable t) {
            m.logE("ValueAnimator scale", t);
        }
        for (Method method : Settings.Global.class.getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (!method.getName().equals("getFloat") || p.length < 2 || p[0] != ContentResolver.class) continue;
            m.hookSafely(method, chain -> {
                Config c = m.config();
                if (c != null && !Float.isNaN(c.animScale)
                        && Settings.Global.ANIMATOR_DURATION_SCALE.equals(chain.getArg(1))) {
                    return c.animScale;
                }
                return chain.proceed();
            });
        }
    }

    static void onResume(Config c, Activity a) {
        // Never cleared here: the app sets the flag itself for video.
        if (c.keepScreenOn) a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
