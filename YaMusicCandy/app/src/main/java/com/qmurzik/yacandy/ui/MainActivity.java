package com.qmurzik.yacandy.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.qmurzik.yacandy.Keys;

import java.io.InputStream;
import java.util.Locale;

import io.github.libxposed.service.XposedService;

public class MainActivity extends Activity implements Store.Listener {
    private static final int REQ_FONT = 1;

    private Store store;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Store.init(this);
        store = Store.get();
        store.addListener(this);
        build();
    }

    @Override
    protected void onDestroy() {
        store.removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onServiceChanged() {
        updateStatus();
    }

    // ---------------------------------------------------------------- layout

    private LinearLayout column;

    private void build() {
        getWindow().getDecorView().setBackgroundColor(Ui.night(this) ? 0xFF140E19 : 0xFFF8F0F6);
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        column.setPadding(p, p, p, Ui.dp(this, 48));
        scroll.addView(column);
        setContentView(scroll);

        TextView title = Ui.text(this, "Конфетка 🍬", 30, true, Ui.textColor(this));
        column.addView(title);
        TextView sub = Ui.text(this, "Кастомизация интерфейса Яндекс Музыки", 15, false, Ui.subColor(this));
        column.addView(sub);

        status = Ui.text(this, "", 14, false, Color.WHITE);
        int sp = Ui.dp(this, 12);
        status.setPadding(sp, sp, sp, sp);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(0, Ui.dp(this, 14), 0, 0);
        column.addView(status, slp);
        updateStatus();

        presets();
        colorsSection();
        fontSection();
        shapeSection();
        viewsSection();
        applySection();
    }

    private void updateStatus() {
        if (status == null) return;
        XposedService s = store.service();
        String text;
        int color;
        if (s != null && store.connected()) {
            String fw;
            try {
                fw = s.getFrameworkName() + " " + s.getFrameworkVersion() + " · API " + s.getApiVersion();
            } catch (Throwable t) {
                fw = "LSPosed";
            }
            text = "✓ Модуль активен (" + fw + ")\nИзменения применяются на лету.";
            color = 0xFF2E9E6B;
        } else {
            text = "Модуль не подключён к LSPosed.\nВключите его в LSPosed Manager (область: Яндекс Музыка) "
                    + "и перезапустите это приложение. Настройки сохранятся и отправятся позже.";
            color = 0xFFD9534F;
        }
        status.setText(text);
        status.setBackground(Ui.rounded(color, Ui.dp(this, 14)));
    }

    private LinearLayout card(String title, String description) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, p);
        card.setBackground(Ui.rounded(Ui.cardColor(this), Ui.dp(this, 20)));
        card.addView(Ui.text(this, title, 19, true, Ui.textColor(this)));
        if (description != null) {
            TextView d = Ui.text(this, description, 13, false, Ui.subColor(this));
            d.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            card.addView(d);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, Ui.dp(this, 16), 0, 0);
        column.addView(card, lp);
        return card;
    }

    private void presets() {
        LinearLayout card = card("Готовые темы", "Одно касание — и всё настроено. Потом можно подкрутить детали.");
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < Presets.NAMES.length; i++) {
            final int index = i;
            TextView chip = Ui.text(this, Presets.NAMES[i], 14, true, Ui.textColor(this));
            int cp = Ui.dp(this, 10);
            chip.setPadding(cp, cp, Ui.dp(this, 14), cp);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Presets.SWATCH[i]);
            dot.setStroke(Ui.dp(this, 1), 0x33808080);
            int ds = Ui.dp(this, 18);
            dot.setSize(ds, ds);
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
            chip.setCompoundDrawablePadding(Ui.dp(this, 8));
            chip.setBackground(Ui.rounded(Ui.night(this) ? 0xFF33263D : 0xFFF6E6F0, Ui.dp(this, 22)));
            chip.setOnClickListener(v -> {
                store.putAll(Presets.values(index));
                Toast.makeText(this, "Тема «" + Presets.NAMES[index] + "» применена", Toast.LENGTH_SHORT).show();
                recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, Ui.dp(this, 8), Ui.dp(this, 8), 0);
            row.addView(chip, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        card.addView(hs);
    }

    private void colorsSection() {
        LinearLayout c = card("Цвета", "Перекраска работает на уровне отрисовки, поэтому затрагивает и обычные экраны, и Compose.");
        switchRow(c, "Включить перекраску", null, Keys.COLORS_ON, false);

        switchRow(c, "Свой акцентный цвет", "Заменяет фирменный жёлтый (или любой оттенок из диапазона ниже).",
                Keys.ACCENT_ON, true);
        colorRow(c, "Акцент", Keys.ACCENT_COLOR, Keys.DEF_ACCENT);
        TextView rangeLabel = Ui.caption(c.getContext(), "");
        c.addView(rangeLabel);
        SeekBar from = Ui.seek(this, 360, store.getInt(Keys.ACCENT_HUE_FROM, Keys.DEF_HUE_FROM));
        SeekBar to = Ui.seek(this, 360, store.getInt(Keys.ACCENT_HUE_TO, Keys.DEF_HUE_TO));
        from.setProgressDrawable(Ui.hueStrip(this));
        to.setProgressDrawable(Ui.hueStrip(this));
        Runnable label = () -> rangeLabel.setText(String.format(Locale.ROOT,
                "Какие оттенки заменять: %d° – %d° (жёлтый ≈ 45°)", from.getProgress(), to.getProgress()));
        label.run();
        bindSeek(from, Keys.ACCENT_HUE_FROM, label);
        bindSeek(to, Keys.ACCENT_HUE_TO, label);
        c.addView(from);
        c.addView(to);

        sliderRow(c, "Сдвиг оттенка остальных цветов", Keys.HUE_SHIFT, 0, 0, 359, "%d°");
        sliderRow(c, "Насыщенность", Keys.SATURATION, 100, 0, 200, "%d%%");

        switchRow(c, "Фон тёмной темы", "Чёрные и тёмно-серые поверхности.", Keys.DARK_BG_ON, false);
        colorRow(c, "Цвет тёмного фона", Keys.DARK_BG_COLOR, Keys.DEF_DARK_BG);
        switchRow(c, "Фон светлой темы", "Белые и светло-серые поверхности.", Keys.LIGHT_BG_ON, false);
        colorRow(c, "Цвет светлого фона", Keys.LIGHT_BG_COLOR, Keys.DEF_LIGHT_BG);
        switchRow(c, "Цвет текста и иконок", "Почти белые (в тёмной теме) / почти чёрные (в светлой).", Keys.TEXT_ON, false);
        colorRow(c, "Текст в тёмной теме", Keys.TEXT_DARK_COLOR, Keys.DEF_TEXT_DARK);
        colorRow(c, "Текст в светлой теме", Keys.TEXT_LIGHT_COLOR, Keys.DEF_TEXT_LIGHT);

        textArea(c, "Точная замена цветов",
                "По строке на правило: #FFDB4D = #FF6FB5\n#AARRGGBB тоже можно. Применяется раньше остальных правил.",
                Keys.COLOR_MAP);
    }

    private void fontSection() {
        LinearLayout c = card("Шрифт", "Подменяет шрифт всего приложения с сохранением жирности.");
        String[] names = {"Как в приложении", "Узкий (Condensed)", "С засечками (Serif)", "Моноширинный",
                "Casual", "Рукописный (Cursive)", "Капитель", "Свой файл…"};
        choiceRow(c, "Шрифт", Keys.FONT_SOURCE, Keys.FONT_IDS, names, Keys.FONT_OFF, id -> {
            if (Keys.FONT_CUSTOM.equals(id)) pickFont();
        });
        Button pick = button(c, "Выбрать файл шрифта (.ttf / .otf)");
        pick.setOnClickListener(v -> pickFont());
        sliderRow(c, "Толщина", Keys.FONT_WEIGHT_DELTA, 0, -300, 300, "%+d");
        sliderRow(c, "Размер текста", Keys.FONT_SCALE, 100, 70, 160, "%d%%");
    }

    private void shapeSection() {
        LinearLayout c = card("Форма и размер", null);
        sliderRow(c, "Масштаб интерфейса", Keys.UI_SCALE, 100, 70, 140, "%d%%");
        switchRow(c, "Свои скругления", "Обложки, карточки, кнопки, плашки.", Keys.CORNERS_ON, false);
        sliderRow(c, "Сила скругления", Keys.CORNER_FACTOR, 100, 0, 400, "%d%%");
        switchRow(c, "Не трогать круглые элементы", "Аватарки, круглые кнопки и «таблетки».", Keys.KEEP_CIRCLES, true);
    }

    private void viewsSection() {
        LinearLayout c = card("Скрытие элементов",
                "Работает для классических View. Включите инспектор, откройте нужный экран — "
                        + "дерево с id появится в логах LSPosed.");
        textArea(c, "Скрыть по id", "По строке: id ресурса, например promo_banner", Keys.HIDE_IDS);
        textArea(c, "Скрыть по тексту", "По строке: часть текста или описания элемента", Keys.HIDE_TEXTS);
        switchRow(c, "Инспектор экранов", "Пишет структуру каждого открытого экрана в лог.", Keys.INSPECTOR, false);
    }

    private void applySection() {
        LinearLayout c = card("Применение", null);
        switchRow(c, "Применять сразу", "Открытый экран Яндекс Музыки пересоздаётся после изменения.",
                Keys.LIVE_APPLY, true);
        button(c, "Перезапустить Яндекс Музыку").setOnClickListener(v -> restartTarget());
        button(c, "Сбросить все настройки").setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Вернуть всё как было?")
                .setPositiveButton("Сбросить", (d, w) -> {
                    store.clearAll();
                    recreate();
                })
                .setNegativeButton("Отмена", null)
                .show());
    }

    // ---------------------------------------------------------------- rows

    private void switchRow(LinearLayout parent, String title, String desc, String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, title, 16, false, Ui.textColor(this)));
        if (desc != null) texts.addView(Ui.text(this, desc, 12, false, Ui.subColor(this)));
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(store.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, checked) -> store.putBoolean(key, checked));
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        parent.addView(row);
    }

    private void colorRow(LinearLayout parent, String title, String key, int def) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        TextView t = Ui.text(this, title, 15, false, Ui.textColor(this));
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        int color = store.getInt(key, def);
        TextView hex = Ui.text(this, ColorPicker.toHex(color), 13, false, Ui.subColor(this));
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setPadding(0, 0, Ui.dp(this, 10), 0);
        row.addView(hex);
        int sz = Ui.dp(this, 32);
        View sw = Ui.swatch(this, color, sz);
        row.addView(sw, new LinearLayout.LayoutParams(sz, sz));
        row.setOnClickListener(v -> ColorPicker.show(this, title, store.getInt(key, def), picked -> {
            store.putInt(key, picked);
            hex.setText(ColorPicker.toHex(picked));
            ((GradientDrawable) sw.getBackground()).setColor(picked);
        }));
        parent.addView(row);
    }

    private void sliderRow(LinearLayout parent, String title, String key, int def, int min, int max, String fmt) {
        TextView label = Ui.caption(this, "");
        parent.addView(label);
        SeekBar seek = Ui.seek(this, max - min, store.getInt(key, def) - min);
        Runnable update = () -> label.setText(title + ": " + String.format(Locale.ROOT, fmt, seek.getProgress() + min));
        update.run();
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                update.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                store.putInt(key, s.getProgress() + min);
            }
        });
        // Long-press the label to reset.
        label.setOnLongClickListener(v -> {
            seek.setProgress(def - min);
            store.putInt(key, def);
            return true;
        });
        parent.addView(seek);
    }

    private void bindSeek(SeekBar seek, String key, Runnable onChange) {
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                onChange.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                store.putInt(key, s.getProgress());
            }
        });
    }

    private interface OnChoice {
        void onChoice(String id);
    }

    private void choiceRow(LinearLayout parent, String title, String key, String[] ids, String[] names,
                           String def, OnChoice cb) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        row.addView(Ui.text(this, title, 16, false, Ui.textColor(this)), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = Ui.text(this, names[indexOf(ids, store.getString(key, def))], 14, true, Ui.BRAND);
        row.addView(value);
        row.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(names, indexOf(ids, store.getString(key, def)), (d, which) -> {
                    store.putString(key, ids[which]);
                    value.setText(names[which]);
                    d.dismiss();
                    cb.onChoice(ids[which]);
                })
                .show());
        parent.addView(row);
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    private void textArea(LinearLayout parent, String title, String hint, String key) {
        parent.addView(Ui.caption(this, title));
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setMinLines(2);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setHint(hint);
        e.setTextColor(Ui.textColor(this));
        e.setHintTextColor(Ui.subColor(this));
        e.setTypeface(Typeface.MONOSPACE);
        e.setTextSize(13);
        e.setText(store.getString(key, ""));
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
        Button save = button(parent, "Сохранить");
        save.setOnClickListener(v -> {
            store.putString(key, e.getText().toString());
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        });
    }

    private Button button(LinearLayout parent, String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(Ui.rounded(Ui.BRAND, Ui.dp(this, 14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 8), 0, 0);
        parent.addView(b, lp);
        return b;
    }

    // ---------------------------------------------------------------- actions

    private void pickFont() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf",
                "application/x-font-otf", "application/font-sfnt", "application/octet-stream"});
        try {
            startActivityForResult(i, REQ_FONT);
        } catch (Exception e) {
            Toast.makeText(this, "Нет приложения для выбора файлов", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FONT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        new Thread(() -> {
            String msg;
            try {
                // Validate first: a broken file would silently fall back to the stock font.
                java.io.File tmp = new java.io.File(getCacheDir(), "font_check");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(tmp)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                Typeface.createFromFile(tmp);
                try (InputStream in = new java.io.FileInputStream(tmp)) {
                    store.writeFont(in);
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                store.putString(Keys.FONT_SOURCE, Keys.FONT_CUSTOM);
                msg = "Шрифт установлен";
            } catch (Throwable t) {
                msg = "Не удалось установить шрифт: " + t.getMessage();
            }
            String m = msg;
            runOnUiThread(() -> {
                Toast.makeText(this, m, Toast.LENGTH_LONG).show();
                recreate();
            });
        }).start();
    }

    private void restartTarget() {
        new Thread(() -> {
            boolean ok;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                        "am force-stop " + Keys.TARGET_PACKAGE + " && monkey -p " + Keys.TARGET_PACKAGE
                                + " -c android.intent.category.LAUNCHER 1"});
                ok = p.waitFor() == 0;
            } catch (Exception e) {
                ok = false;
            }
            boolean done = ok;
            runOnUiThread(() -> {
                if (done) {
                    Toast.makeText(this, "Яндекс Музыка перезапущена", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Нет root — остановите приложение вручную", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + Keys.TARGET_PACKAGE)));
                }
            });
        }).start();
    }
}
