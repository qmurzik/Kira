package com.qmurzik.yacandy.hook;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.SparseArray;

import com.qmurzik.yacandy.Keys;

/** Resolves the replacement typeface for whatever the app asks {@link Paint} to use. */
final class Fonts {
    private Fonts() {
    }

    private static final Object LOCK = new Object();
    private static boolean baseLoaded;
    private static Typeface base;
    private static final SparseArray<Typeface> variants = new SparseArray<>();

    static void reset() {
        synchronized (LOCK) {
            baseLoaded = false;
            base = null;
            variants.clear();
        }
    }

    static void install(CandyModule m) {
        try {
            m.hookSafely(Paint.class.getDeclaredMethod("setTypeface", Typeface.class), chain -> {
                Config c = m.config();
                if (c == null || !c.fontOn()) return chain.proceed();
                Typeface orig = (Typeface) chain.getArg(0);
                Typeface rep = resolve(m, c, orig);
                return rep == orig ? chain.proceed() : chain.proceed(new Object[]{rep});
            });
        } catch (Throwable t) {
            m.logE("font hooks", t);
        }
    }

    static Typeface resolve(CandyModule m, Config c, Typeface orig) {
        if (orig == Typeface.MONOSPACE) return orig; // keep timers/code-like text aligned
        int weight;
        boolean italic;
        if (orig == null) {
            weight = 400;
            italic = false;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            weight = orig.getWeight();
            italic = orig.isItalic();
        } else {
            weight = orig.isBold() ? 700 : 400;
            italic = orig.isItalic();
        }
        weight = Math.max(100, Math.min(1000, weight + c.fontWeightDelta));
        int key = weight * 2 + (italic ? 1 : 0);
        synchronized (LOCK) {
            Typeface cached = variants.get(key);
            if (cached != null) return cached;
            Typeface family = loadBase(m, c);
            if (family == null) {
                if (c.fontWeightDelta == 0) return orig;
                family = orig != null ? orig : Typeface.DEFAULT;
            }
            Typeface out;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                out = Typeface.create(family, weight, italic);
            } else {
                int style = (weight >= 600 ? Typeface.BOLD : 0) | (italic ? Typeface.ITALIC : 0);
                out = Typeface.create(family, style);
            }
            // A null-base fallback depends on the original family, don't cache it by weight only.
            if (base != null) variants.put(key, out);
            return out;
        }
    }

    private static Typeface loadBase(CandyModule m, Config c) {
        if (baseLoaded) return base;
        baseLoaded = true;
        base = null;
        if (Keys.FONT_CUSTOM.equals(c.fontSource)) {
            try (ParcelFileDescriptor pfd = m.openRemoteFile(Keys.FONT_FILE)) {
                base = new Typeface.Builder(pfd.getFileDescriptor()).build();
            } catch (Throwable t) {
                m.logE("custom font load failed", t);
            }
        } else {
            for (int i = 0; i < Keys.FONT_IDS.length; i++) {
                if (Keys.FONT_IDS[i].equals(c.fontSource) && Keys.FONT_FAMILIES[i] != null) {
                    base = Typeface.create(Keys.FONT_FAMILIES[i], Typeface.NORMAL);
                }
            }
        }
        return base;
    }
}
