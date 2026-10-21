package com.qmurzik.yacandy.hook;

import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.SweepGradient;
import android.os.Build;
import android.view.Window;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Recolors at the graphics layer: both classic Views and Jetpack Compose end up in
 * {@link Paint#setColor}, shader constructors, color filters or {@link Canvas#drawColor}.
 */
final class ColorHooks {
    private ColorHooks() {
    }

    static void install(CandyModule m) {
        try {
            m.hookSafely(Paint.class.getDeclaredMethod("setColor", int.class), chain -> {
                ColorEngine e = m.colors();
                if (e == null) return chain.proceed();
                int c = (Integer) chain.getArg(0);
                int out = e.map(c);
                return out == c ? chain.proceed() : chain.proceed(new Object[]{out});
            });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                m.hookSafely(Paint.class.getDeclaredMethod("setColor", long.class), chain -> {
                    ColorEngine e = m.colors();
                    if (e == null) return chain.proceed();
                    long c = (Long) chain.getArg(0);
                    long out = e.mapLong(c);
                    return out == c ? chain.proceed() : chain.proceed(new Object[]{out});
                });
            }
        } catch (Throwable t) {
            m.logE("Paint hooks", t);
        }

        // Hardware rendering goes through BaseRecordingCanvas which overrides the Canvas methods.
        hookCanvas(m, Canvas.class);
        try {
            hookCanvas(m, Class.forName("android.graphics.BaseRecordingCanvas"));
        } catch (Throwable ignored) {
            // pre-Q: RecordingCanvas did not exist under this name
        }

        hookColorArgs(m, LinearGradient.class);
        hookColorArgs(m, RadialGradient.class);
        hookColorArgs(m, SweepGradient.class);
        hookColorArgs(m, PorterDuffColorFilter.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hookColorArgs(m, BlendModeColorFilter.class);
        }

        hookWindowBars(m);
    }

    private static void hookCanvas(CandyModule m, Class<?> cls) {
        for (Method method : cls.getDeclaredMethods()) {
            String n = method.getName();
            Class<?>[] p = method.getParameterTypes();
            if (Modifier.isAbstract(method.getModifiers())) continue;
            if (n.equals("drawColor") && p.length >= 1 && (p[0] == int.class || p[0] == long.class)) {
                m.hookSafely(method, chain -> {
                    ColorEngine e = m.colors();
                    if (e == null) return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    if (args[0] instanceof Integer) {
                        args[0] = e.map((Integer) args[0]);
                    } else {
                        args[0] = e.mapLong((Long) args[0]);
                    }
                    return chain.proceed(args);
                });
            } else if ((n.equals("drawARGB") && p.length == 4) || (n.equals("drawRGB") && p.length == 3)) {
                final boolean hasAlpha = p.length == 4;
                m.hookSafely(method, chain -> {
                    ColorEngine e = m.colors();
                    if (e == null) return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    int i = hasAlpha ? 1 : 0;
                    int a = hasAlpha ? (Integer) args[0] : 255;
                    int c = e.map(Color.argb(a, (Integer) args[i], (Integer) args[i + 1], (Integer) args[i + 2]));
                    if (hasAlpha) args[0] = Color.alpha(c);
                    args[i] = Color.red(c);
                    args[i + 1] = Color.green(c);
                    args[i + 2] = Color.blue(c);
                    return chain.proceed(args);
                });
            }
        }
    }

    /**
     * Hooks every public constructor and maps all color-typed arguments. In the gradient and
     * color filter classes every {@code int}/{@code long} (array) parameter is a color.
     */
    private static void hookColorArgs(CandyModule m, Class<?> cls) {
        for (Constructor<?> ctor : cls.getDeclaredConstructors()) {
            if (!Modifier.isPublic(ctor.getModifiers())) continue;
            boolean hasColor = false;
            for (Class<?> t : ctor.getParameterTypes()) {
                if (t == int.class || t == long.class || t == int[].class || t == long[].class) {
                    hasColor = true;
                    break;
                }
            }
            if (!hasColor) continue;
            m.hookSafely(ctor, chain -> {
                ColorEngine e = m.colors();
                if (e == null) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                for (int i = 0; i < args.length; i++) {
                    Object a = args[i];
                    if (a instanceof Integer) {
                        args[i] = e.map((Integer) a);
                    } else if (a instanceof Long) {
                        args[i] = e.mapLong((Long) a);
                    } else if (a instanceof int[]) {
                        int[] src = (int[]) a;
                        int[] dst = new int[src.length];
                        for (int k = 0; k < src.length; k++) dst[k] = e.map(src[k]);
                        args[i] = dst;
                    } else if (a instanceof long[]) {
                        long[] src = (long[]) a;
                        long[] dst = new long[src.length];
                        for (int k = 0; k < src.length; k++) dst[k] = e.mapLong(src[k]);
                        args[i] = dst;
                    }
                }
                return chain.proceed(args);
            });
        }
    }

    private static void hookWindowBars(CandyModule m) {
        try {
            Class<?> phoneWindow = Class.forName("com.android.internal.policy.PhoneWindow");
            for (String name : new String[]{"setStatusBarColor", "setNavigationBarColor"}) {
                Method method;
                try {
                    method = phoneWindow.getDeclaredMethod(name, int.class);
                } catch (NoSuchMethodException e) {
                    method = Window.class.getDeclaredMethod(name, int.class);
                    if (Modifier.isAbstract(method.getModifiers())) continue;
                }
                m.hookSafely(method, chain -> {
                    ColorEngine e = m.colors();
                    if (e == null) return chain.proceed();
                    return chain.proceed(new Object[]{e.map((Integer) chain.getArg(0))});
                });
            }
        } catch (Throwable t) {
            m.logE("window bar hooks", t);
        }
    }
}
