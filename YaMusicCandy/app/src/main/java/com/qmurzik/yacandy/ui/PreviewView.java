package com.qmurzik.yacandy.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import com.qmurzik.yacandy.Keys;

/** A miniature Yandex Music player screen drawn in a given palette. */
final class PreviewView extends View {

    /** The colors of one theme variant (dark or light). */
    static final class Palette {
        final int bg, surface, text, accent, onAccent, like;

        Palette(int bg, int surface, int text, int accent, int onAccent, int like) {
            this.bg = bg;
            this.surface = surface;
            this.text = text;
            this.accent = accent;
            this.onAccent = onAccent;
            this.like = like;
        }

        static Palette original(boolean night) {
            return night
                    ? new Palette(0xFF000000, 0xFF141414, 0xFFFFFFFF, 0xFFFFE033, 0xFF000000, 0xFFF84C35)
                    : new Palette(0xFFFFFFFF, 0xFFF6F5F3, 0xFF000000, 0xFFFFE033, 0xFF000000, 0xFFF84C35);
        }

        /** Palette of a preset (see {@link Presets}). */
        static Palette preset(int index, boolean night) {
            int[] p = Presets.palette(index);
            if (p == null) {
                Palette o = original(night);
                if (index != Presets.NAMES.length - 1 || !night) return o;
                return new Palette(0xFF000000, 0xFF0A0A0A, o.text, o.accent, o.onAccent, o.like);
            }
            return night
                    ? new Palette(p[2], p[3], p[6], p[0], onAccent(p[0]), p[1])
                    : new Palette(p[4], p[5], p[7], p[0], onAccent(p[0]), p[1]);
        }

        /** What the hooked app will look like with the current settings. */
        static Palette current(Store s, boolean night) {
            Palette o = original(night);
            if (!s.getBoolean(Keys.COLORS_ON, false)) return o;
            int bg = o.bg, surface = o.surface, text = o.text, accent = o.accent, like = o.like;
            if (night && s.getBoolean(Keys.DARK_BG_ON, false)) {
                bg = s.getInt(Keys.DARK_BG_COLOR, Keys.DEF_DARK_BG);
                surface = s.getInt(Keys.DARK_SURFACE_COLOR, Keys.DEF_DARK_SURFACE);
            }
            if (!night && s.getBoolean(Keys.LIGHT_BG_ON, false)) {
                bg = s.getInt(Keys.LIGHT_BG_COLOR, Keys.DEF_LIGHT_BG);
                surface = s.getInt(Keys.LIGHT_SURFACE_COLOR, Keys.DEF_LIGHT_SURFACE);
            }
            if (s.getBoolean(Keys.TEXT_ON, false)) {
                text = night ? s.getInt(Keys.TEXT_DARK_COLOR, Keys.DEF_TEXT_DARK)
                        : s.getInt(Keys.TEXT_LIGHT_COLOR, Keys.DEF_TEXT_LIGHT);
            }
            if (s.getBoolean(Keys.ACCENT_ON, true)) accent = s.getInt(Keys.ACCENT_COLOR, Keys.DEF_ACCENT);
            if (s.getBoolean(Keys.LIKE_ON, false)) like = s.getInt(Keys.LIKE_COLOR, Keys.DEF_LIKE);
            String on = s.getString(Keys.ON_ACCENT, "auto");
            int onAccent = "black".equals(on) ? Color.BLACK : "white".equals(on) ? Color.WHITE : onAccent(accent);
            return new Palette(bg, surface, text, accent, onAccent, like);
        }

        private static int onAccent(int accent) {
            double l = (0.2126 * Color.red(accent) + 0.7152 * Color.green(accent) + 0.0722 * Color.blue(accent)) / 255;
            return l > 0.45 ? Color.BLACK : Color.WHITE;
        }
    }

    private Palette palette = Palette.original(true);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();

    PreviewView(Context c) {
        super(c);
    }

    void setPalette(Palette p) {
        palette = p;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        Palette p = palette;
        float u = w / 100f; // layout unit: 1% of the width

        // Screen
        paint.setShader(null);
        paint.setColor(p.bg);
        r.set(0, 0, w, h);
        c.drawRoundRect(r, 12 * u, 12 * u, paint);

        // Header text
        line(c, 10 * u, 9 * u, 44 * u, 4.2f * u, p.text, 0.9f);
        line(c, 10 * u, 16 * u, 28 * u, 3.2f * u, p.text, 0.5f);

        // Cover art (artwork is never recolored)
        float coverTop = 24 * u, coverSize = 80 * u;
        r.set(10 * u, coverTop, 10 * u + coverSize, coverTop + coverSize);
        paint.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                new int[]{0xFFFF8FB1, 0xFF8B6CFF, 0xFF3A7BFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 8 * u, 8 * u, paint);
        paint.setShader(null);

        // Title, artist, like
        float y = coverTop + coverSize + 9 * u;
        line(c, 10 * u, y, 50 * u, 4.6f * u, p.text, 0.9f);
        line(c, 10 * u, y + 7.5f * u, 32 * u, 3.4f * u, p.text, 0.5f);
        heart(c, 85 * u, y + 2.5f * u, 5.5f * u, p.like);

        // Progress
        float py = y + 16 * u;
        line(c, 10 * u, py, 80 * u, 1.6f * u, p.text, 0.2f);
        line(c, 10 * u, py, 34 * u, 1.6f * u, p.accent, 1f);
        paint.setColor(p.accent);
        c.drawCircle(44 * u, py + 0.8f * u, 2.6f * u, paint);

        // Controls
        float cy = py + 15 * u;
        paint.setColor(p.accent);
        c.drawCircle(50 * u, cy, 10 * u, paint);
        triangle(c, 47.5f * u, cy, 6 * u, p.onAccent, true);
        triangle(c, 30 * u, cy, 5 * u, withAlpha(p.text, 0.9f), false);
        triangle(c, 70 * u, cy, 5 * u, withAlpha(p.text, 0.9f), true);

        // A card below the player (surface)
        float top = cy + 15 * u;
        if (top + 14 * u < h) {
            paint.setColor(p.surface);
            r.set(6 * u, top, 94 * u, Math.min(h - 5 * u, top + 30 * u));
            c.drawRoundRect(r, 6 * u, 6 * u, paint);
            line(c, 12 * u, top + 6 * u, 40 * u, 3.6f * u, p.text, 0.9f);
            line(c, 12 * u, top + 12.5f * u, 58 * u, 3f * u, p.text, 0.5f);
        }
    }

    private void line(Canvas c, float x, float y, float len, float th, int color, float alpha) {
        paint.setColor(withAlpha(color, alpha));
        r.set(x, y, x + len, y + th);
        c.drawRoundRect(r, th / 2, th / 2, paint);
    }

    private void triangle(Canvas c, float cx, float cy, float s, int color, boolean right) {
        paint.setColor(color);
        path.reset();
        float d = right ? 1 : -1;
        path.moveTo(cx - d * s * 0.45f, cy - s * 0.6f);
        path.lineTo(cx + d * s * 0.75f, cy);
        path.lineTo(cx - d * s * 0.45f, cy + s * 0.6f);
        path.close();
        c.drawPath(path, paint);
    }

    private void heart(Canvas c, float cx, float cy, float s, int color) {
        paint.setColor(color);
        path.reset();
        path.moveTo(cx, cy + s * 0.45f);
        path.cubicTo(cx - s * 1.1f, cy - s * 0.25f, cx - s * 0.45f, cy - s * 0.95f, cx, cy - s * 0.35f);
        path.cubicTo(cx + s * 0.45f, cy - s * 0.95f, cx + s * 1.1f, cy - s * 0.25f, cx, cy + s * 0.45f);
        path.close();
        c.drawPath(path, paint);
    }

    private static int withAlpha(int color, float a) {
        return (Math.round(Color.alpha(color) * a) << 24) | (color & 0xFFFFFF);
    }
}
