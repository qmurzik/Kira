package com.qmurzik.yacandy.hook;

import android.graphics.Color;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps every color the app draws to the user's palette. Yandex Music's design tokens (studied on
 * 2026.09.5) are few and exact, so colors are assigned to roles instead of being guessed by hue:
 * <pre>
 *            night                         day
 * page bg    #000000                       #FFFFFF
 * surfaces   #0D0D0D #141414 #1F1F1F...    #F6F5F3 #F7F7F7 #EDEDED...
 * text/icons white at 90/50/30% alpha      black at 90/50/30% alpha
 * on accent  #E6000000 (only black text in the night theme)
 * accent     #FFFF00 #FFEE00 #FFF780 #FFDB4D (Plus: #FCE000 #FAC220), links #0858F4 (day)
 * like       #F84C35
 * </pre>
 * Alpha is always kept, so secondary/disabled text and translucent overlays keep their hierarchy.
 * Called from very hot paths (Paint.setColor), so results are cached in a lock-free table.
 */
final class ColorEngine {
    private static final int CACHE_SIZE = 1 << 13;
    private static final int CACHE_MASK = CACHE_SIZE - 1;

    private static final int LIKE = 0xF84C35;
    private static final int LINK_DAY = 0x0858F4;

    private final Config cfg;
    private final boolean night;
    private final float[] accentHsv = new float[3];
    private final int onAccent;
    /** Each slot packs {@code key << 32 | value}; 0 means empty (key 0 is never cached). */
    private final long[] cache = new long[CACHE_SIZE];
    /** Colors this engine produced; they are never mapped again (View code reads colors back). */
    private final ConcurrentHashMap<Integer, Boolean> produced = new ConcurrentHashMap<>();

    ColorEngine(Config cfg, boolean night) {
        this.cfg = cfg;
        this.night = night;
        Color.colorToHSV(cfg.accentColor, accentHsv);
        onAccent = cfg.onAccent != 0 ? cfg.onAccent : autoOnAccent(cfg.accentColor);
    }

    boolean isNight() {
        return night;
    }

    int map(int argb) {
        if (argb == 0) return 0;
        int idx = (argb ^ (argb >>> 13) ^ (argb >>> 23)) & CACHE_MASK;
        long slot = cache[idx];
        if (slot != 0 && (int) (slot >>> 32) == argb) return (int) slot;
        int out = produced.containsKey(argb) ? argb : compute(argb);
        cache[idx] = ((long) argb << 32) | (out & 0xFFFFFFFFL);
        if (out != argb && out != 0) {
            if (produced.size() < 4096) produced.put(out, Boolean.TRUE);
            int oidx = (out ^ (out >>> 13) ^ (out >>> 23)) & CACHE_MASK;
            cache[oidx] = ((long) out << 32) | (out & 0xFFFFFFFFL);
        }
        return out;
    }

    /** Maps a packed {@code long} color; only sRGB colors are touched. */
    long mapLong(long color) {
        if (!Color.isSrgb(color)) return color;
        int argb = Color.toArgb(color);
        int out = map(argb);
        return out == argb ? color : Color.pack(out);
    }

    private int compute(int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0) return argb;
        int rgb = argb & 0xFFFFFF;

        Long exact = cfg.colorMap.get(rgb);
        if (exact != null) {
            long v = exact;
            if ((v & Config.EXPLICIT_ALPHA) != 0) return (int) v;
            return withAlpha((int) v, alpha);
        }

        float[] hsv = new float[3];
        Color.colorToHSV(argb, hsv);
        float h = hsv[0], s = hsv[1], v = hsv[2];

        if (s <= 0.12f) return neutral(argb, alpha, v);
        if (cfg.likeOn && near(rgb, LIKE, 24)) return withAlpha(cfg.likeColor, alpha);
        if (cfg.accentOn) {
            if (!night && cfg.accentLinks && near(rgb, LINK_DAY, 30)) return withAlpha(cfg.accentColor, alpha);
            if (s >= 0.30f && v >= 0.30f && ColorEngine.inHueRange(h, cfg.hueFrom, cfg.hueTo)) {
                // Keep the relative shading of the original (pressed/disabled variants etc).
                hsv[0] = accentHsv[0];
                hsv[1] = clamp(accentHsv[1] * Math.min(1f, s / 0.85f));
                hsv[2] = clamp(accentHsv[2] * Math.min(1.1f, v / 0.97f));
                return Color.HSVToColor(alpha, hsv);
            }
        }
        boolean changed = false;
        if (cfg.hueShift != 0 && s >= 0.20f) {
            hsv[0] = (h + cfg.hueShift) % 360f;
            changed = true;
        }
        if (Math.abs(cfg.saturation - 1f) > 0.001f) {
            hsv[1] = clamp(s * cfg.saturation);
            changed = true;
        }
        return changed ? Color.HSVToColor(alpha, hsv) : argb;
    }

    /** Greys: backgrounds, surfaces, text and icons depending on the theme. */
    private int neutral(int argb, int alpha, float v) {
        if (night) {
            if (v <= 0.30f) {
                // The only translucent near-black in the night theme is text on accent buttons;
                // other translucent blacks are scrims/shadows and stay black.
                if (alpha < 255) {
                    if (cfg.accentOn && alpha >= 0xC0 && v <= 0.05f) return withAlpha(onAccent, alpha);
                    return argb;
                }
                if (!cfg.darkBgOn) return argb;
                // #000 -> background, #141414 -> surface, lighter greys keep their elevation step.
                if (v <= 0.08f) return withAlpha(mix(cfg.darkBg, cfg.darkSurface, v / 0.08f), 255);
                return withAlpha(mix(cfg.darkSurface, 0xFFFFFFFF, (v - 0.08f) * 0.9f), 255);
            }
            if (v >= 0.80f && cfg.textOn) return withAlpha(cfg.textDark, alpha);
            return argb;
        }
        if (v >= 0.86f) {
            if (alpha < 255) return argb; // translucent whites are highlights over images
            if (!cfg.lightBgOn) return argb;
            // #FFF -> background, #F6F5F3 -> surface, #EDEDED.. darker steps.
            if (v >= 0.96f) return withAlpha(mix(cfg.lightBg, cfg.lightSurface, (1f - v) / 0.04f), 255);
            return withAlpha(mix(cfg.lightSurface, 0xFF000000, (0.96f - v) * 0.9f), 255);
        }
        if (v <= 0.30f && cfg.textOn) return withAlpha(cfg.textLight, alpha);
        return argb;
    }

    /** Black or white text on the accent, whichever reads better. */
    static int autoOnAccent(int accent) {
        double l = luminance(accent);
        return (1.05 / (l + 0.05)) >= ((l + 0.05) / 0.05) ? 0xFFFFFFFF : 0xFF000000;
    }

    private static double luminance(int c) {
        return 0.2126 * lin(Color.red(c)) + 0.7152 * lin(Color.green(c)) + 0.0722 * lin(Color.blue(c));
    }

    private static double lin(int ch) {
        double v = ch / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static boolean near(int rgb, int ref, int tol) {
        return Math.abs(((rgb >> 16) & 0xFF) - ((ref >> 16) & 0xFF)) <= tol
                && Math.abs(((rgb >> 8) & 0xFF) - ((ref >> 8) & 0xFF)) <= tol
                && Math.abs((rgb & 0xFF) - (ref & 0xFF)) <= tol;
    }

    static boolean inHueRange(float h, int from, int to) {
        if (from <= to) return h >= from && h <= to;
        return h >= from || h <= to; // range wraps through 0 (reds)
    }

    private static float clamp(float f) {
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    /** Linear mix of {@code a} towards {@code b} by {@code t}. */
    private static int mix(int a, int b, float t) {
        t = clamp(t);
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
