package com.qmurzik.yacandy.hook;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Scales corner radii of everything rounded: view/Compose outlines (clips of covers, cards),
 * round-rect backgrounds and round-rect paths.
 */
final class CornerHooks {
    private CornerHooks() {
    }

    static void install(CandyModule m) {
        try {
            // Outline.setRoundRect(Rect, float) delegates to this one.
            m.hookSafely(Outline.class.getDeclaredMethod("setRoundRect",
                    int.class, int.class, int.class, int.class, float.class), chain -> {
                Config c = m.config();
                if (c == null || !c.cornersOn) return chain.proceed();
                Object[] a = chain.getArgs().toArray();
                float w = (Integer) a[2] - (Integer) a[0];
                float h = (Integer) a[3] - (Integer) a[1];
                a[4] = radius(c, (Float) a[4], w, h);
                return chain.proceed(a);
            });
        } catch (Throwable t) {
            m.logE("outline hook", t);
        }

        // RectF overloads delegate to the float ones; only most-derived implementations are
        // hooked so a radius is never scaled twice.
        hookDrawRoundRect(m, Canvas.class);
        try {
            hookDrawRoundRect(m, Class.forName("android.graphics.BaseRecordingCanvas"));
        } catch (Throwable ignored) {
        }

        try {
            m.hookSafely(Path.class.getDeclaredMethod("addRoundRect", float.class, float.class,
                    float.class, float.class, float.class, float.class, Path.Direction.class), chain -> {
                Config c = m.config();
                if (c == null || !c.cornersOn) return chain.proceed();
                Object[] a = chain.getArgs().toArray();
                float w = (Float) a[2] - (Float) a[0];
                float h = (Float) a[3] - (Float) a[1];
                a[4] = radius(c, (Float) a[4], w, h);
                a[5] = radius(c, (Float) a[5], w, h);
                return chain.proceed(a);
            });
            m.hookSafely(Path.class.getDeclaredMethod("addRoundRect", float.class, float.class,
                    float.class, float.class, float[].class, Path.Direction.class), chain -> {
                Config c = m.config();
                if (c == null || !c.cornersOn) return chain.proceed();
                Object[] a = chain.getArgs().toArray();
                float[] radii = (float[]) a[4];
                if (radii == null) return chain.proceed();
                float w = (Float) a[2] - (Float) a[0];
                float h = (Float) a[3] - (Float) a[1];
                float[] out = Arrays.copyOf(radii, radii.length);
                for (int i = 0; i < out.length; i++) out[i] = radius(c, out[i], w, h);
                a[4] = out;
                return chain.proceed(a);
            });
        } catch (Throwable t) {
            m.logE("path hooks", t);
        }
    }

    private static void hookDrawRoundRect(CandyModule m, Class<?> cls) {
        try {
            Method method = cls.getDeclaredMethod("drawRoundRect", float.class, float.class,
                    float.class, float.class, float.class, float.class,
                    android.graphics.Paint.class);
            m.hookSafely(method, chain -> {
                Config c = m.config();
                if (c == null || !c.cornersOn) return chain.proceed();
                Object[] a = chain.getArgs().toArray();
                float w = (Float) a[2] - (Float) a[0];
                float h = (Float) a[3] - (Float) a[1];
                a[4] = radius(c, (Float) a[4], w, h);
                a[5] = radius(c, (Float) a[5], w, h);
                return chain.proceed(a);
            });
        } catch (NoSuchMethodException ignored) {
        }
    }

    static float radius(Config c, float r, float w, float h) {
        if (r <= 0f) return r;
        float max = Math.min(Math.abs(w), Math.abs(h)) / 2f;
        if (max <= 0f) return r;
        if (c.keepCircles && r >= max - 0.5f) return r; // avatars, pills, round buttons
        return Math.min(r * c.cornerFactor, max);
    }
}
