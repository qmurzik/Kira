package com.qmurzik.yacandy.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import java.util.Locale;

/** HSV + hex color picker with a palette of candy swatches. */
final class ColorPicker {
    interface OnPicked {
        void onPicked(int color);
    }

    private static final int[] PALETTE = {
            0xFFFF6FB5, 0xFFFF4D8D, 0xFFFF8A50, 0xFFFFC94D, 0xFFFFDB4D, 0xFFA6E36B,
            0xFF3DDC97, 0xFF2EC4B6, 0xFF4FC3F7, 0xFF5C7CFA, 0xFF8C6CFF, 0xFFB388FF,
            0xFFFFFFFF, 0xFFE0E0E0, 0xFF808080, 0xFF202020, 0xFF14101C, 0xFF000000};

    private ColorPicker() {
    }

    static void show(Context ctx, String title, int initial, OnPicked cb) {
        int pad = Ui.dp(ctx, 20);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad / 2, pad, 0);

        View preview = new View(ctx);
        GradientDrawable pd = new GradientDrawable();
        pd.setCornerRadius(Ui.dp(ctx, 14));
        preview.setBackground(pd);
        root.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(ctx, 56)));

        float[] hsv = new float[3];
        Color.colorToHSV(initial, hsv);
        int[] alpha = {Color.alpha(initial)};

        SeekBar hue = Ui.seek(ctx, 360, Math.round(hsv[0]));
        SeekBar sat = Ui.seek(ctx, 100, Math.round(hsv[1] * 100));
        SeekBar val = Ui.seek(ctx, 100, Math.round(hsv[2] * 100));
        hue.setProgressDrawable(Ui.hueStrip(ctx));
        root.addView(Ui.caption(ctx, "Оттенок"));
        root.addView(hue);
        root.addView(Ui.caption(ctx, "Насыщенность"));
        root.addView(sat);
        root.addView(Ui.caption(ctx, "Яркость"));
        root.addView(val);

        EditText hex = new EditText(ctx);
        hex.setSingleLine();
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
        hex.setHint("#RRGGBB");
        root.addView(hex);

        boolean[] syncing = {false};
        Runnable fromSliders = () -> {
            int c = Color.HSVToColor(alpha[0], new float[]{hue.getProgress(), sat.getProgress() / 100f, val.getProgress() / 100f});
            pd.setColor(c);
            syncing[0] = true;
            hex.setText(toHex(c));
            syncing[0] = false;
        };
        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) fromSliders.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        };
        hue.setOnSeekBarChangeListener(l);
        sat.setOnSeekBarChangeListener(l);
        val.setOnSeekBarChangeListener(l);

        OnPicked setAll = c -> {
            float[] h = new float[3];
            Color.colorToHSV(c, h);
            alpha[0] = Color.alpha(c);
            hue.setProgress(Math.round(h[0]));
            sat.setProgress(Math.round(h[1] * 100));
            val.setProgress(Math.round(h[2] * 100));
            pd.setColor(c);
        };
        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncing[0]) return;
                Integer c = parse(s.toString());
                if (c != null) setAll.onPicked(c);
            }
        });

        LinearLayout swatches = new LinearLayout(ctx);
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        int sz = Ui.dp(ctx, 34);
        for (int c : PALETTE) {
            View sw = Ui.swatch(ctx, c, sz);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
            lp.setMargins(0, 0, Ui.dp(ctx, 8), 0);
            sw.setOnClickListener(v -> {
                setAll.onPicked(c);
                syncing[0] = true;
                hex.setText(toHex(c));
                syncing[0] = false;
            });
            swatches.addView(sw, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(swatches);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.setMargins(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 4));
        root.addView(hs, hlp);

        setAll.onPicked(initial);
        hex.setText(toHex(initial));

        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(root)
                .setPositiveButton("Готово", (d, w) -> {
                    Integer c = parse(hex.getText().toString());
                    if (c != null) cb.onPicked(c);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    static String toHex(int c) {
        if (Color.alpha(c) == 255) return String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF);
        return String.format(Locale.ROOT, "#%08X", c);
    }

    static Integer parse(String s) {
        String t = s.trim().replace("#", "");
        try {
            if (t.length() == 6) return 0xFF000000 | Integer.parseInt(t, 16);
            if (t.length() == 8) return (int) Long.parseLong(t, 16);
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
}
