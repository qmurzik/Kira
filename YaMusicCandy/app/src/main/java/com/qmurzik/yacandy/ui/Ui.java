package com.qmurzik.yacandy.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Design tokens and view factories of the settings app (built in code, no XML layouts). */
final class Ui {
    private Ui() {
    }

    // Brand gradient, taken from the Yume icon.
    static final int BRAND = 0xFF8B6CFF;
    static final int PINK = 0xFFC77DFF;
    static final int BLUE = 0xFF5B7CFF;
    static final int GOOD = 0xFF3DD68C;
    static final int BAD = 0xFFFF5C7A;

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static int bgColor(Context c) {
        return night(c) ? 0xFF0B0A1A : 0xFFF5F2FF;
    }

    static int cardColor(Context c) {
        return night(c) ? 0xFF17142B : 0xFFFFFFFF;
    }

    static int card2Color(Context c) {
        return night(c) ? 0xFF221D3D : 0xFFEDE8FC;
    }

    static int textColor(Context c) {
        return night(c) ? 0xFFEDE7FF : 0xFF1B1530;
    }

    static int subColor(Context c) {
        return night(c) ? 0x99EDE7FF : 0x8C1B1530;
    }

    static int dividerColor(Context c) {
        return night(c) ? 0x14FFFFFF : 0x121B1530;
    }

    static Drawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable gradient(float radiusPx, int... colors) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** Background with a touch ripple, clipped to the same rounded shape. */
    static Drawable pressable(Context c, Drawable content, float radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(night(c) ? 0x22FFFFFF : 0x1A1B1530), content,
                rounded(Color.WHITE, radiusPx));
    }

    static TextView text(Context c, CharSequence s, float sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    static TextView caption(Context c, CharSequence s) {
        TextView t = text(c, s, 13, false, subColor(c));
        t.setPadding(0, dp(c, 10), 0, dp(c, 2));
        return t;
    }

    /** Small-caps section title above a group card. */
    static TextView sectionTitle(Context c, String s) {
        TextView t = text(c, s.toUpperCase(java.util.Locale.ROOT), 12, true, BRAND);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(c, 6), dp(c, 22), 0, dp(c, 8));
        return t;
    }

    /** A rounded card that holds rows separated by thin dividers. */
    static LinearLayout group(Context c) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(rounded(cardColor(c), dp(c, 22)));
        g.setClipToOutline(true);
        g.setDividerDrawable(divider(c));
        g.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return g;
    }

    private static Drawable divider(Context c) {
        GradientDrawable line = new GradientDrawable();
        line.setColor(dividerColor(c));
        line.setSize(1, Math.max(1, dp(c, 1) / 2));
        LayerDrawable inset = new LayerDrawable(new Drawable[]{line});
        inset.setLayerInset(0, dp(c, 18), 0, dp(c, 18), 0);
        return inset;
    }

    /** Row skeleton: title + optional description on the left, an optional trailing view. */
    static LinearLayout row(Context c, String title, String desc, View trailing) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(c, 58));
        row.setPadding(dp(c, 18), dp(c, 12), dp(c, 14), dp(c, 12));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(c, title, 16, false, textColor(c)));
        if (desc != null) {
            TextView d = text(c, desc, 13, false, subColor(c));
            d.setPadding(0, dp(c, 2), 0, 0);
            texts.addView(d);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        if (trailing != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginStart(dp(c, 12));
            row.addView(trailing, lp);
        }
        row.setBackground(pressable(c, null, 0));
        return row;
    }

    static Switch toggle(Context c, boolean checked) {
        Switch sw = new Switch(c);
        sw.setChecked(checked);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{Color.WHITE, night(c) ? 0xFFB9B2D6 : 0xFFFFFFFF}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{BRAND, night(c) ? 0xFF3A345A : 0xFFD5CFEA}));
        return sw;
    }

    /** Value chip shown at the end of choice / slider rows. */
    static TextView chip(Context c, String s) {
        TextView t = text(c, s, 14, true, BRAND);
        t.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        t.setBackground(rounded(night(c) ? 0x268B6CFF : 0x1A8B6CFF, dp(c, 14)));
        return t;
    }

    static SeekBar seek(Context c, int max, int progress) {
        SeekBar s = new SeekBar(c);
        s.setMax(max);
        s.setProgress(progress);
        s.setProgressTintList(ColorStateList.valueOf(BRAND));
        s.setThumbTintList(ColorStateList.valueOf(BRAND));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(night(c) ? 0xFF3A345A : 0xFFD5CFEA));
        return s;
    }

    /** Gradient pill button. */
    static TextView primaryButton(Context c, String s) {
        TextView b = text(c, s, 16, true, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 20), dp(c, 15), dp(c, 20), dp(c, 15));
        b.setBackground(pressable(c, gradient(dp(c, 26), BRAND, PINK, BLUE), dp(c, 26)));
        b.setElevation(dp(c, 3));
        return b;
    }

    static TextView secondaryButton(Context c, String s, int color) {
        TextView b = text(c, s, 15, true, color);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        b.setBackground(pressable(c, rounded(cardColor(c), dp(c, 24)), dp(c, 24)));
        return b;
    }

    /** Round icon bubble with a glyph (emoji) on the brand gradient. */
    static TextView bubble(Context c, String glyph, int size, int... colors) {
        TextView t = new TextView(c);
        t.setText(glyph);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.48f);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setShape(GradientDrawable.OVAL);
        t.setBackground(d);
        t.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return t;
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
        d.setStroke(dp(c, 2), night(c) ? 0x33FFFFFF : 0x221B1530);
        d.setSize(size, size);
        v.setBackground(d);
        return v;
    }

    interface OnPick {
        void onPick(int index);
    }

    interface Styler {
        void style(TextView item, int index);
    }

    /** Bottom sheet with a list of options; the selected one is marked. */
    static void sheet(Activity a, String title, String[] items, int selected, Styler styler, OnPick cb) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 20);
        box.setPadding(0, dp(a, 10), 0, dp(a, 18));
        // Keep the last items above the navigation bar (dialogs are edge to edge on Android 15+).
        box.setOnApplyWindowInsetsListener((v, insets) -> {
            @SuppressWarnings("deprecation") int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(0, dp(a, 10), 0, dp(a, 18) + bottom);
            return insets;
        });
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardColor(a));
        float r = dp(a, 28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        box.setBackground(bg);
        View handle = new View(a);
        handle.setBackground(rounded(dividerColor(a) | 0x22000000, dp(a, 3)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(a, 40), dp(a, 5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = dp(a, 10);
        box.addView(handle, hl);
        TextView t = text(a, title, 20, true, textColor(a));
        t.setPadding(p, dp(a, 4), p, dp(a, 10));
        box.addView(t);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView item = text(a, items[i], 17, i == selected, i == selected ? BRAND : textColor(a));
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setMinHeight(dp(a, 52));
            item.setPadding(p, 0, p, 0);
            if (i == selected) {
                item.setBackground(pressable(a, rounded(night(a) ? 0x268B6CFF : 0x148B6CFF, dp(a, 16)), dp(a, 16)));
            } else {
                item.setBackground(pressable(a, null, dp(a, 16)));
            }
            if (styler != null) styler.style(item, i);
            item.setOnClickListener(v -> {
                d.dismiss();
                cb.onPick(index);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(dp(a, 10), 0, dp(a, 10), 0);
            list.addView(item, lp);
        }
        ScrollView sv = new ScrollView(a);
        sv.addView(list);
        box.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout root = new FrameLayout(a);
        root.addView(box, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
            // Limit to ~75% of the screen so long lists scroll inside the sheet.
            int max = Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.75f);
            box.measure(View.MeasureSpec.makeMeasureSpec(a.getResources().getDisplayMetrics().widthPixels,
                    View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            if (box.getMeasuredHeight() > max) {
                box.getLayoutParams().height = max;
                sv.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
            }
        }
        d.show();
    }
}
