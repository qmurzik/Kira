package com.qmurzik.yacandy.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

/** Small view factory helpers; the settings screen is built in code. */
final class Ui {
    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static int cardColor(Context c) {
        return night(c) ? 0xFF231A2B : 0xFFFFFFFF;
    }

    static int textColor(Context c) {
        return night(c) ? 0xFFF5EAF7 : 0xFF241628;
    }

    static int subColor(Context c) {
        return night(c) ? 0xB3F5EAF7 : 0x99241628;
    }

    static final int BRAND = 0xFFFF6FB5;

    static Drawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static TextView text(Context c, CharSequence s, float sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    static TextView caption(Context c, CharSequence s) {
        TextView t = text(c, s, 13, false, subColor(c));
        t.setPadding(0, dp(c, 10), 0, dp(c, 2));
        return t;
    }

    static SeekBar seek(Context c, int max, int progress) {
        SeekBar s = new SeekBar(c);
        s.setMax(max);
        s.setProgress(progress);
        return s;
    }

    static Drawable hueStrip(Context c) {
        int[] colors = new int[13];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = Color.HSVToColor(new float[]{i * 30f, 0.85f, 1f});
        }
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        d.setCornerRadius(dp(c, 6));
        d.setSize(dp(c, 200), dp(c, 12));
        return d;
    }

    static View swatch(Context c, int color, int size) {
        View v = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        d.setStroke(dp(c, 1), 0x33808080);
        d.setSize(size, size);
        v.setBackground(d);
        return v;
    }
}
