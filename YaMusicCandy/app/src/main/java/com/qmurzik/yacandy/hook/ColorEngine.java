package com.qmurzik.yacandy.hook;

import android.graphics.Color;

/**
 * Maps every color the app draws to its "candy" counterpart. Called from very hot paths
 * (Paint.setColor), so results are kept in a lock-free direct-mapped cache.
 */
final class ColorEngine {
    private static final int CACHE_SIZE = 1 << 13;
    private static final int CACHE_MASK = CACHE_SIZE - 1;

    private final Config cfg;
    private final boolean night;
    /** Each slot packs {@code key << 32 | value}; 0 means empty (key 0 is never cached). */
    private final long[] cache = new long[CACHE_SIZE];

    ColorEngine(Config cfg, boolean night) {
        this.cfg = cfg;
        this.night = night;
    }

    boolean isNight() {
        return night;
    }

    int map(int argb) {
        if (argb == 0) return 0;
        int idx = (argb ^ (argb >>> 13) ^ (argb >>> 23)) & CACHE_MASK;
        long slot = cache[idx];
        if (slot != 0 && (int) (slot >>> 32) == argb) return (int) slot;
        int out = compute(argb);
        cache[idx] = ((long) argb << 32) | (out & 0xFFFFFFFFL);
        if (out != argb && out != 0) {
            // Remember produced colors as fixed points so a color that is read back and set
            // again (very common in View code) is not transformed twice.
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
            return (alpha << 24) | ((int) v & 0xFFFFFF);
        }

        float[] hsv = new float[3];
        Color.colorToHSV(argb, hsv);
        float h = hsv[0], s = hsv[1], v = hsv[2];

        // Neutral surfaces & text: only the rules for the current theme apply, otherwise white
        // text in the dark theme would be treated as a light background (and vice versa).
        if (s <= 0.18f) {
            if (night) {
                if (cfg.darkBgOn && v <= 0.22f && s <= 0.18f) {
                    return withAlpha(mix(cfg.darkBg, 0xFFFFFFFF, v * 0.85f), alpha);
                }
                if (cfg.textOn && v >= 0.80f && s <= 0.12f) {
                    return withAlpha(mix(cfg.textDark, 0xFFFFFFFF, (v - 0.8f) * 0.5f), alpha);
                }
            } else {
                if (cfg.lightBgOn && v >= 0.92f && s <= 0.08f) {
                    return withAlpha(mix(cfg.lightBg, 0xFF000000, (1f - v) * 0.85f), alpha);
                }
                if (cfg.textOn && v <= 0.25f && s <= 0.12f) {
                    return withAlpha(mix(cfg.textLight, 0xFF000000, (0.25f - v) * 0.5f), alpha);
                }
            }
            return argb;
        }

        boolean changed = false;
        if (cfg.accentOn && s >= 0.30f && v >= 0.30f && inHueRange(h, cfg.hueFrom, cfg.hueTo)) {
            float[] t = new float[3];
            Color.colorToHSV(cfg.accentColor, t);
            // Keep the relative shading of the original (pressed/disabled variants etc).
            h = t[0];
            s = clamp(t[1] * Math.min(1f, s / 0.75f));
            v = clamp(t[2] * Math.min(1.15f, v / 0.95f));
            changed = true;
        } else if (cfg.hueShift != 0 && s >= 0.20f) {
            h = (h + cfg.hueShift) % 360f;
            changed = true;
        }
        if (Math.abs(cfg.saturation - 1f) > 0.001f) {
            s = clamp(s * cfg.saturation);
            changed = true;
        }
        if (!changed) return argb;
        hsv[0] = h;
        hsv[1] = s;
        hsv[2] = v;
        return Color.HSVToColor(alpha, hsv);
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
