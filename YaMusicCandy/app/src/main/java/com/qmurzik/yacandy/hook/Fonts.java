package com.qmurzik.yacandy.hook;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.SparseArray;

import com.qmurzik.yacandy.Keys;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Resolves the replacement typeface for whatever the app asks {@link Paint} to use. There are two
 * slots: regular text and bold text / headings (weight 600+), each a system family or a font file
 * (bundled with the module or the user's own). Variable fonts get their real weight through the
 * 'wght' axis instead of synthetic bold.
 */
final class Fonts {
    private Fonts() {
    }

    private static final int TEXT = 0;
    private static final int BOLD = 1;

    private static final Object LOCK = new Object();
    private static final boolean[] loaded = new boolean[2];
    private static final Typeface[] bases = new Typeface[2];
    private static final File[] files = new File[2];
    private static final SparseArray<Typeface> variants = new SparseArray<>();

    static void reset() {
        synchronized (LOCK) {
            loaded[TEXT] = loaded[BOLD] = false;
            bases[TEXT] = bases[BOLD] = null;
            files[TEXT] = files[BOLD] = null;
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
        int origWeight;
        boolean italic;
        if (orig == null) {
            origWeight = 400;
            italic = false;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            origWeight = orig.getWeight();
            italic = orig.isItalic();
        } else {
            origWeight = orig.isBold() ? 700 : 400;
            italic = orig.isItalic();
        }
        int slot = c.boldFontOn() && origWeight >= 600 ? BOLD : TEXT;
        String source = slot == BOLD ? c.fontBoldSource : c.fontSource;
        int weight = Math.max(100, Math.min(1000, origWeight + c.fontWeightDelta));
        int key = (slot << 12) | (weight << 1) | (italic ? 1 : 0);
        synchronized (LOCK) {
            Typeface cached = variants.get(key);
            if (cached != null) return cached;
            load(m, c, slot, source);
            Typeface out = null;
            if (files[slot] != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                out = variable(files[slot], weight, italic);
            }
            if (out == null) {
                Typeface family = bases[slot];
                if (family == null) {
                    if (c.fontWeightDelta == 0) return orig;
                    // Weight only: depends on the original family, so it isn't cached by weight.
                    return withWeight(orig != null ? orig : Typeface.DEFAULT, weight, italic);
                }
                out = withWeight(family, weight, italic);
            }
            variants.put(key, out);
            return out;
        }
    }

    private static Typeface withWeight(Typeface family, int weight, boolean italic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return Typeface.create(family, weight, italic);
        int style = (weight >= 600 ? Typeface.BOLD : 0) | (italic ? Typeface.ITALIC : 0);
        return Typeface.create(family, style);
    }

    /** A font file instance at the given weight; static fonts simply ignore the axis. */
    private static Typeface variable(File f, int weight, boolean italic) {
        try {
            return new Typeface.Builder(f)
                    .setFontVariationSettings("'wght' " + weight)
                    .setWeight(weight)
                    .setItalic(italic)
                    .build();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void load(CandyModule m, Config c, int slot, String source) {
        if (loaded[slot]) return;
        loaded[slot] = true;
        bases[slot] = null;
        files[slot] = null;
        if (Config.isFileFont(source)) {
            String remote = slot == BOLD ? Keys.FONT_BOLD_FILE : Keys.FONT_FILE;
            int version = slot == BOLD ? c.fontBoldVersion : c.fontVersion;
            File f = copyRemote(m, remote, version);
            if (f != null) {
                try {
                    bases[slot] = new Typeface.Builder(f).build();
                    files[slot] = f;
                } catch (Throwable t) {
                    m.logE("font load failed: " + source, t);
                }
            }
        } else {
            for (int i = 0; i < Keys.FONT_IDS.length; i++) {
                if (Keys.FONT_IDS[i].equals(source) && Keys.FONT_FAMILIES[i] != null) {
                    bases[slot] = Typeface.create(Keys.FONT_FAMILIES[i], Typeface.NORMAL);
                }
            }
        }
    }

    /** Copies the module's shared font file into the app's cache, once per font version. */
    private static File copyRemote(CandyModule m, String remote, int version) {
        File dir = new File(m.appDataDir(), "cache/yacandy_fonts");
        File f = new File(dir, remote + "_" + version + ".ttf");
        if (f.isFile() && f.length() > 0) return f;
        try {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File[] old = dir.listFiles((d, name) -> name.startsWith(remote + "_"));
            if (old != null) for (File o : old) //noinspection ResultOfMethodCallIgnored
                o.delete();
            File tmp = new File(dir, remote + ".tmp");
            try (ParcelFileDescriptor pfd = m.openRemoteFile(remote);
                 InputStream in = new FileInputStream(pfd.getFileDescriptor());
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return tmp.renameTo(f) ? f : null;
        } catch (Throwable t) {
            m.logE("font copy failed: " + remote, t);
            return null;
        }
    }
}
