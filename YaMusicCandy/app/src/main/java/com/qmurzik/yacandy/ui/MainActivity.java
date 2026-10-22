package com.qmurzik.yacandy.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.qmurzik.yacandy.Features;
import com.qmurzik.yacandy.Keys;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.libxposed.service.XposedService;

/**
 * Settings app: a home screen (status, themes, sections) and one page per section, all built in
 * code. Pages are pushed onto a simple in-activity stack with a slide animation.
 */
public class MainActivity extends Activity implements Store.Listener {
    private static final int REQ_FONT = 1;
    private static final int REQ_FONT_BOLD = 2;
    private static final String RESTART = "Вступает в силу после перезапуска Музыки.";

    private Store store;
    private FrameLayout stage;
    private final Deque<View> pages = new ArrayDeque<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private TextView statusChip;
    /** Compatibility report and app version when opened from Yandex Music. */
    private String appStatus;
    private String appVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Store.init(this);
        store = Store.get();
        store.addListener(this);
        readIntent(getIntent());
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Ui.bgColor(this));
        stage = new FrameLayout(this);
        stage.setBackgroundColor(Ui.bgColor(this));
        // Android 15+ always draws apps edge to edge. Pages are added later than the first insets
        // pass, so the stage keeps the latest insets and pads every page itself.
        stage.setOnApplyWindowInsetsListener((v, insets) -> {
            barInsets = systemBars(insets);
            for (View page : pages) applyInsets(page);
            return insets;
        });
        setContentView(stage);
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::pop;
        }
        show(home(), false);
        String page = getIntent() != null ? getIntent().getStringExtra(Keys.EXTRA_PAGE) : null;
        if (page != null) open(page);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readIntent(intent);
    }

    private void readIntent(Intent i) {
        if (i == null) return;
        if (i.hasExtra(Keys.EXTRA_STATUS)) appStatus = i.getStringExtra(Keys.EXTRA_STATUS);
        if (i.hasExtra(Keys.EXTRA_APP_VERSION)) appVersion = i.getStringExtra(Keys.EXTRA_APP_VERSION);
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

    /** Left, top, right, bottom system bar (and cutout) insets. */
    private int[] barInsets = new int[4];
    private android.window.OnBackInvokedCallback backCallback;
    private boolean backRegistered;

    @SuppressWarnings("deprecation")
    private static int[] systemBars(WindowInsets in) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            return new int[]{i.left, i.top, i.right, i.bottom};
        }
        return new int[]{in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom()};
    }

    private void applyInsets(View page) {
        page.setPadding(barInsets[0], barInsets[1], barInsets[2], barInsets[3]);
    }

    /**
     * Predictive back (Android 13+, mandatory with targetSdk 36) no longer calls onBackPressed:
     * while a sub-page is open a callback takes the gesture, on the home page it is removed so the
     * system can close the app.
     */
    private void updateBackCallback() {
        if (Build.VERSION.SDK_INT < 33 || backCallback == null) return;
        boolean need = pages.size() > 1;
        if (need == backRegistered) return;
        if (need) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        } else {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        backRegistered = need;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (pages.size() > 1) {
            pop();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------- navigation

    private void show(View page, boolean animate) {
        View top = pages.peek();
        pages.push(page);
        applyInsets(page);
        stage.addView(page);
        updateBackCallback();
        if (!animate || top == null) return;
        page.setTranslationX(getResources().getDisplayMetrics().widthPixels * 0.3f);
        page.setAlpha(0f);
        page.animate().translationX(0).alpha(1f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        top.animate().alpha(0f).setDuration(200).withEndAction(() -> top.setVisibility(View.INVISIBLE)).start();
    }

    private void pop() {
        if (pages.size() <= 1) return;
        View page = pages.pop();
        updateBackCallback();
        View under = pages.peek();
        if (under != null) {
            under.setVisibility(View.VISIBLE);
            under.animate().alpha(1f).setDuration(220).start();
        }
        page.animate().translationX(getResources().getDisplayMetrics().widthPixels * 0.3f).alpha(0f)
                .setDuration(220).withEndAction(() -> stage.removeView(page)).start();
        refreshAll();
    }

    /** Scrollable page column; fits system bars. */
    private LinearLayout pageColumn(ScrollView[] holder) {
        ScrollView scroll = new ScrollView(this);
        // Clip to the inset padding so content never scrolls under the status bar.
        scroll.setClipToPadding(true);
        scroll.setBackgroundColor(Ui.bgColor(this));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        col.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 40));
        scroll.addView(col);
        holder[0] = scroll;
        return col;
    }

    /** A section page with a back button and title. */
    private LinearLayout page(String title, String subtitle, ScrollView[] holder) {
        LinearLayout col = pageColumn(holder);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        TextView back = Ui.text(this, "←", 22, true, Ui.textColor(this));
        back.setGravity(Gravity.CENTER);
        int s = Ui.dp(this, 44);
        back.setBackground(Ui.pressable(this, Ui.rounded(Ui.cardColor(this), s / 2f), s / 2f));
        back.setOnClickListener(v -> pop());
        bar.addView(back, new LinearLayout.LayoutParams(s, s));
        col.addView(bar);
        TextView t = Ui.text(this, title, 30, true, Ui.textColor(this));
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), 0, 0);
        col.addView(t);
        if (subtitle != null) {
            TextView st = Ui.text(this, subtitle, 14, false, Ui.subColor(this));
            st.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 4));
            col.addView(st);
        }
        return col;
    }

    private void open(String key) {
        ScrollView[] h = new ScrollView[1];
        switch (key) {
            case "colors": colorsPage(h); break;
            case "fonts": fontsPage(h); break;
            case "shape": shapePage(h); break;
            case "clean": cleanPage(h); break;
            case "player": playerPage(h); break;
            case "behaviour": behaviourPage(h); break;
            case "experiments": experimentsPage(h); break;
            default: morePage(h); break;
        }
        show(h[0], true);
    }

    private void refreshAll() {
        for (Runnable r : refreshers) r.run();
    }

    // ---------------------------------------------------------------- home

    private View home() {
        ScrollView[] h = new ScrollView[1];
        LinearLayout col = pageColumn(h);
        col.addView(hero());
        if (appVersion != null || appStatus != null) col.addView(compatBanner());

        col.addView(Ui.sectionTitle(this, "Темы"));
        col.addView(themeCarousel());

        col.addView(Ui.sectionTitle(this, "Настройки"));
        String[][] tiles = {
                {"colors", "🎨", "Цвета", "Акцент, фон, карточки, текст"},
                {"fonts", "🔤", "Шрифты", "18 шрифтов с кириллицей"},
                {"shape", "◐", "Форма и размер", "Масштаб, скругления"},
                {"player", "🎧", "Плеер", "Блоки, текст песен, анимации"},
                {"clean", "✨", "Чистый интерфейс", "Промо, баннеры, вкладки"},
                {"behaviour", "⚙", "Поведение", "Телеметрия, автозапуск"},
                {"experiments", "🧪", "Эксперименты", "Флаги Яндекса"},
                {"more", "⋯", "Ещё", "Вход из Музыки, совместимость"},
        };
        int[][] tones = {{0xFF8B6CFF, 0xFFC77DFF}, {0xFF5B7CFF, 0xFF8B6CFF}, {0xFF3DB8FF, 0xFF5B7CFF},
                {0xFFC77DFF, 0xFFFF7EB6}, {0xFFFF7EB6, 0xFFFFB86C}, {0xFF3DD68C, 0xFF3DB8FF},
                {0xFFFFB86C, 0xFFFF7EB6}, {0xFF6E6A8A, 0xFF8B6CFF}};
        LinearLayout rowView = null;
        for (int i = 0; i < tiles.length; i++) {
            if (i % 2 == 0) {
                rowView = new LinearLayout(this);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.bottomMargin = Ui.dp(this, 12);
                col.addView(rowView, rl);
            }
            View tile = tile(tiles[i][1], tiles[i][2], tiles[i][3], tones[i]);
            final String key = tiles[i][0];
            tile.setOnClickListener(v -> open(key));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i % 2 == 0) lp.setMarginEnd(Ui.dp(this, 6));
            else lp.setMarginStart(Ui.dp(this, 6));
            rowView.addView(tile, lp);
        }

        TextView restart = Ui.primaryButton(this, "Перезапустить Яндекс Музыку");
        restart.setOnClickListener(v -> restartTarget());
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = Ui.dp(this, 12);
        col.addView(restart, bl);
        TextView hint = Ui.text(this, "Внешний вид меняется сразу. Плеер, промо и поведение — после перезапуска.",
                12, false, Ui.subColor(this));
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), 0);
        col.addView(hint);
        return h[0];
    }

    private View hero() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        card.setPadding(p, p, p, p);
        GradientDrawable bg = Ui.gradient(Ui.dp(this, 28), 0xFF2A1F5C, 0xFF3B1F57, 0xFF1A2A63);
        card.setBackground(bg);
        card.setElevation(Ui.dp(this, 2));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(getResources().getIdentifier("ic_launcher", "mipmap", getPackageName()));
        icon.setClipToOutline(true);
        icon.setBackground(Ui.rounded(0xFF0C0B22, Ui.dp(this, 18)));
        int is = Ui.dp(this, 64);
        top.addView(icon, new LinearLayout.LayoutParams(is, is));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 16), 0, 0, 0);
        texts.addView(Ui.text(this, "Yume", 30, true, Color.WHITE));
        texts.addView(Ui.text(this, "Модуль для Яндекс Музыки", 14, false, 0xCCFFFFFF));
        top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(top);

        statusChip = Ui.text(this, "", 13, true, Color.WHITE);
        statusChip.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = Ui.dp(this, 16);
        card.addView(statusChip, sl);
        updateStatus();

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 8);
        card.setLayoutParams(lp);
        return card;
    }

    private void updateStatus() {
        if (statusChip == null) return;
        XposedService s = store.service();
        String text;
        int dot;
        if (s != null && store.connected()) {
            String fw;
            try {
                fw = s.getFrameworkName() + " " + s.getFrameworkVersion();
            } catch (Throwable t) {
                fw = "LSPosed";
            }
            text = "●  Активен · " + fw;
            dot = Ui.GOOD;
        } else {
            text = "●  Не подключён к LSPosed";
            dot = Ui.BAD;
        }
        statusChip.setText(text);
        statusChip.setTextColor(Color.WHITE);
        statusChip.setBackground(Ui.rounded((dot & 0x00FFFFFF) | 0x55000000, Ui.dp(this, 16)));
        statusChip.setOnClickListener(store.connected() ? null : v -> new AlertDialog.Builder(this)
                .setMessage("Включите Yume в LSPosed Manager (область: Яндекс Музыка), затем перезапустите "
                        + "это приложение. Настройки сохранятся и отправятся позже.")
                .setPositiveButton("Понятно", null)
                .show());
    }

    private View compatBanner() {
        int total = 0, ok = 0;
        for (String[] line : statusLines()) {
            total++;
            if (!line[1].startsWith("не ")) ok++;
        }
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 16);
        b.setPadding(p, p, p, p);
        b.setBackground(Ui.pressable(this, Ui.rounded(Ui.cardColor(this), Ui.dp(this, 22)), Ui.dp(this, 22)));
        b.addView(Ui.bubble(this, ok == total ? "✓" : "!", Ui.dp(this, 40),
                ok == total ? Ui.GOOD : 0xFFFFB86C, ok == total ? 0xFF3DB8FF : 0xFFFF7EB6));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Ui.dp(this, 14), 0, 0, 0);
        t.addView(Ui.text(this, "Яндекс Музыка " + (appVersion != null ? appVersion : ""), 15, true, Ui.textColor(this)));
        t.addView(Ui.text(this, total == 0 ? "Совместимость: всё на Android-уровне"
                : "Нашлось " + ok + " из " + total + " точек привязки", 13, false, Ui.subColor(this)));
        b.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        b.setOnClickListener(v -> open("more"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 12);
        b.setLayoutParams(lp);
        return b;
    }

    private List<String[]> statusLines() {
        List<String[]> out = new ArrayList<>();
        if (appStatus == null) return out;
        for (String line : appStatus.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab > 0) out.add(new String[]{line.substring(0, tab), line.substring(tab + 1)});
        }
        return out;
    }

    private View themeCarousel() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        boolean night = Ui.night(this);
        List<View> cards = new ArrayList<>();
        for (int i = 0; i < Presets.NAMES.length; i++) {
            final int index = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            int p = Ui.dp(this, 8);
            card.setPadding(p, p, p, Ui.dp(this, 10));
            PreviewView pv = new PreviewView(this);
            pv.setPalette(PreviewView.Palette.preset(i, night));
            card.addView(pv, new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 172)));
            TextView name = Ui.text(this, Presets.NAMES[i], 14, true, Ui.textColor(this));
            name.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(name);
            card.setOnClickListener(v -> {
                store.putAll(Presets.values(index));
                Toast.makeText(this, "Тема «" + Presets.NAMES[index] + "»", Toast.LENGTH_SHORT).show();
                refreshAll();
            });
            cards.add(card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(this, 10));
            row.addView(card, lp);
        }
        Runnable mark = () -> {
            int sel = store.getInt(Keys.PRESET, -1);
            for (int i = 0; i < cards.size(); i++) {
                GradientDrawable d = new GradientDrawable();
                d.setColor(Ui.cardColor(this));
                d.setCornerRadius(Ui.dp(this, 22));
                if (i == sel) d.setStroke(Ui.dp(this, 2), Ui.BRAND);
                cards.get(i).setBackground(Ui.pressable(this, d, Ui.dp(this, 22)));
            }
        };
        mark.run();
        refreshers.add(mark);
        hs.addView(row);
        return hs;
    }

    private View tile(String glyph, String title, String desc, int[] tone) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        t.setPadding(p, p, p, p);
        t.setMinimumHeight(Ui.dp(this, 128));
        t.setBackground(Ui.pressable(this, Ui.rounded(Ui.cardColor(this), Ui.dp(this, 24)), Ui.dp(this, 24)));
        TextView b = Ui.bubble(this, glyph, Ui.dp(this, 42), tone);
        b.setTextColor(Color.WHITE);
        t.addView(b);
        TextView tt = Ui.text(this, title, 16, true, Ui.textColor(this));
        tt.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 2));
        t.addView(tt);
        t.addView(Ui.text(this, desc, 12, false, Ui.subColor(this)));
        return t;
    }

    // ---------------------------------------------------------------- pages

    private void colorsPage(ScrollView[] h) {
        LinearLayout col = page("Цвета", "Палитра собрана по реальным цветам Яндекс Музыки. Каждый цвет "
                + "заменяется по своей роли, прозрачность сохраняется.", h);

        LinearLayout previews = new LinearLayout(this);
        previews.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        previews.setBackground(Ui.rounded(Ui.cardColor(this), Ui.dp(this, 22)));
        for (boolean night : new boolean[]{true, false}) {
            LinearLayout one = new LinearLayout(this);
            one.setOrientation(LinearLayout.VERTICAL);
            one.setGravity(Gravity.CENTER_HORIZONTAL);
            PreviewView pv = new PreviewView(this);
            Runnable r = () -> pv.setPalette(PreviewView.Palette.current(store, night));
            r.run();
            refreshers.add(r);
            one.addView(pv, new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 224)));
            TextView l = Ui.text(this, night ? "Тёмная" : "Светлая", 13, false, Ui.subColor(this));
            l.setPadding(0, Ui.dp(this, 8), 0, 0);
            one.addView(l);
            previews.addView(one, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.topMargin = Ui.dp(this, 14);
        col.addView(previews, pl);

        LinearLayout g = section(col, "Основное");
        switchRow(g, "Перекрашивать Музыку", null, Keys.COLORS_ON, false);

        g = section(col, "Акцент");
        switchRow(g, "Свой акцент", "Вместо фирменного жёлтого: кнопки, прогресс, активные иконки", Keys.ACCENT_ON, true);
        colorRow(g, "Цвет акцента", Keys.ACCENT_COLOR, Keys.DEF_ACCENT);
        choiceRow(g, "Текст на кнопках", Keys.ON_ACCENT, new String[]{"auto", "black", "white"},
                new String[]{"Авто", "Чёрный", "Белый"}, "auto");
        switchRow(g, "Ссылки цветом акцента", "Синие ссылки в светлой теме", Keys.ACCENT_LINKS, true);
        switchRow(g, "Свой цвет лайка", null, Keys.LIKE_ON, false);
        colorRow(g, "Цвет лайка", Keys.LIKE_COLOR, Keys.DEF_LIKE);

        g = section(col, "Тёмная тема");
        switchRow(g, "Свой фон", null, Keys.DARK_BG_ON, false);
        colorRow(g, "Фон экранов", Keys.DARK_BG_COLOR, Keys.DEF_DARK_BG);
        colorRow(g, "Карточки и панели", Keys.DARK_SURFACE_COLOR, Keys.DEF_DARK_SURFACE);

        g = section(col, "Светлая тема");
        switchRow(g, "Свой фон", null, Keys.LIGHT_BG_ON, false);
        colorRow(g, "Фон экранов", Keys.LIGHT_BG_COLOR, Keys.DEF_LIGHT_BG);
        colorRow(g, "Карточки и панели", Keys.LIGHT_SURFACE_COLOR, Keys.DEF_LIGHT_SURFACE);

        g = section(col, "Текст и иконки");
        switchRow(g, "Свой цвет текста", "Основной, вторичный и неактивный текст в одном оттенке", Keys.TEXT_ON, false);
        colorRow(g, "В тёмной теме", Keys.TEXT_DARK_COLOR, Keys.DEF_TEXT_DARK);
        colorRow(g, "В светлой теме", Keys.TEXT_LIGHT_COLOR, Keys.DEF_TEXT_LIGHT);

        g = section(col, "Тонкая настройка");
        sliderRow(g, "Сдвиг оттенка прочих цветов", Keys.HUE_SHIFT, 0, 0, 359, "%d°");
        sliderRow(g, "Насыщенность прочих цветов", Keys.SATURATION, 100, 0, 200, "%d%%");
        sliderRow(g, "Акцентом считать оттенки от", Keys.ACCENT_HUE_FROM, Keys.DEF_HUE_FROM, 0, 359, "%d°");
        sliderRow(g, "…до", Keys.ACCENT_HUE_TO, Keys.DEF_HUE_TO, 0, 359, "%d°");
        textArea(g, "Точная замена цветов", "#FFDB4D = #FF6FB5\n#AARRGGBB тоже можно", Keys.COLOR_MAP);
    }

    private static final String[] SYSTEM_FONT_NAMES = {"Как в приложении", "Узкий (Condensed)",
            "С засечками (Serif)", "Моноширинный", "Casual", "Рукописный (Cursive)", "Капитель"};

    private void fontsPage(ScrollView[] h) {
        LinearLayout col = page("Шрифты", "18 встроенных шрифтов с кириллицей (SIL OFL) или свой файл. "
                + "Вариативные шрифты получают настоящую толщину.", h);
        LinearLayout g = section(col, "Начертание");
        fontRow(g, "Основной текст", false);
        fontRow(g, "Заголовки и жирный", true);
        g = section(col, "Размер и толщина");
        sliderRow(g, "Толщина", Keys.FONT_WEIGHT_DELTA, 0, -300, 300, "%+d");
        sliderRow(g, "Размер текста", Keys.FONT_SCALE, 100, 70, 160, "%d%%");
    }

    private void shapePage(ScrollView[] h) {
        LinearLayout col = page("Форма и размер", null, h);
        LinearLayout g = section(col, "Масштаб");
        sliderRow(g, "Масштаб интерфейса", Keys.UI_SCALE, 100, 70, 140, "%d%%");
        g = section(col, "Скругления");
        switchRow(g, "Свои скругления", "Обложки, карточки, кнопки, плашки", Keys.CORNERS_ON, false);
        sliderRow(g, "Сила скругления", Keys.CORNER_FACTOR, 100, 0, 400, "%d%%");
        switchRow(g, "Не трогать круглые элементы", "Аватарки, круглые кнопки и «таблетки»", Keys.KEEP_CIRCLES, true);
    }

    private void cleanPage(ScrollView[] h) {
        LinearLayout col = page("Чистый интерфейс", "Убирает промо и лишние блоки. " + RESTART, h);
        LinearLayout g = section(col, "Блоки на главной и у артистов");
        for (Features.BlockGroup b : Features.BLOCK_GROUPS) setSwitchRow(g, b.title, b.desc, Keys.HIDE_BLOCKS, b.id);
        g = section(col, "Промо");
        switchRow(g, "Всплывающие предложения", "Шторки и попапы по триггерам сервера", Keys.NO_POPUPS, false);
        switchRow(g, "Рекламная заставка", null, Keys.NO_SPLASH, false);
        switchRow(g, "Спецшапка главной", null, Keys.NO_SPECIAL_HEADER, false);
        switchRow(g, "Промо в рекомендациях", null, Keys.NO_PROMO_RECS, false);
        switchRow(g, "Квесты Плюса", null, Keys.NO_PLUS_QUESTS, false);
        switchRow(g, "Виджет Плюса", null, Keys.NO_PLUS_WIDGET, false);
        switchRow(g, "Вкладка «Концерты»", null, Keys.NO_CONCERTS_TAB, false);
        switchRow(g, "Рекламные уведомления", "Уведомления плеера и загрузок остаются", Keys.NO_PROMO_PUSH, false);
        caption(col, "Включённый переключатель — элемент скрыт.");
        g = section(col, "Своё");
        textArea(g, "Скрыть вкладки главной", "Часть названия, по строке: Подкасты", Keys.HIDE_TABS);
        textArea(g, "Свои блоки", "Имя DTO или тип с сервера, по строке", Keys.HIDE_BLOCKS_CUSTOM);
        switchRow(g, "Журнал блоков", "Типы блоков и вкладок пишутся в лог LSPosed", Keys.FEED_LOG, false);
    }

    private void playerPage(ScrollView[] h) {
        LinearLayout col = page("Плеер", RESTART, h);
        LinearLayout g = section(col, "Скрыть под плеером");
        switchRow(g, "Похожие треки", null, Keys.PB_SIMILAR, false);
        switchRow(g, "Блок об артисте", null, Keys.PB_ARTISTS, false);
        switchRow(g, "Концерты артиста", null, Keys.PB_CONCERTS, false);
        switchRow(g, "Описание книг и подкастов", null, Keys.PB_NONMUSIC, false);
        switchRow(g, "Информеры", "Плашки-подсказки и промо в плеере", Keys.NO_INFORMERS, false);
        switchRow(g, "«Слова в волне»", null, Keys.NO_WAVE_WORDS, false);
        g = section(col, "Вид");
        switchRow(g, "Полноэкранный текст песни", "Экспериментальный режим Яндекса", Keys.LYRICS_FULLSCREEN, false);
        switchRow(g, "Спокойная волна", "Без тряски, шейдеров и свечения. Экономит батарею", Keys.CALM_WAVE, false);
        choiceRow(g, "Скорость анимаций", Keys.ANIM_SCALE, Keys.ANIM_IDS,
                new String[]{"Как в системе", "Без анимаций", "Быстрее ×2", "Быстрее ×1,3", "Обычная", "Медленнее ×1,5"},
                Keys.ANIM_SYSTEM);
        switchRow(g, "Не гасить экран", "Пока Музыка открыта", Keys.KEEP_SCREEN_ON, false);
    }

    private void behaviourPage(ScrollView[] h) {
        LinearLayout col = page("Поведение", RESTART, h);
        LinearLayout g = section(col, "Приватность");
        switchRow(g, "Отключить телеметрию", "AppMetrica, AppsFlyer, Crashlytics. Статистика прослушиваний "
                + "остаётся: от неё зависят рекомендации и выплаты артистам", Keys.NO_TELEMETRY, false);
        g = section(col, "Запуск и напоминания");
        switchRow(g, "Не включать музыку при запуске", null, Keys.NO_AUTOPLAY, false);
        switchRow(g, "Без напоминаний об обновлении", null, Keys.NO_UPDATE_PROMPTS, false);
        switchRow(g, "Без просьб про батарею", null, Keys.NO_BATTERY_NAG, false);
        switchRow(g, "Пропускаемый визард", "Онбординг «выберите артистов» можно закрыть", Keys.NO_WIZARD, false);
        g = section(col, "Дополнительно");
        switchRow(g, "Настройка «Меньше ИИ»", "Появится в настройках Музыки", Keys.AI_REDUCTION, false);
        switchRow(g, "Telegram и WhatsApp в «Поделиться»", null, Keys.MESSENGER_SHARE, false);
    }

    private void experimentsPage(ScrollView[] h) {
        LinearLayout col = page("Эксперименты", "Серверные флаги Яндекса. Значения: on, default (выключено), "
                + "control и другие. " + RESTART, h);
        LinearLayout g = section(col, "Флаги");
        switchRow(g, "Записать текущие флаги в лог", "Через 20 секунд после запуска Музыки", Keys.EXP_DUMP, false);
        textArea(g, "Свои значения", "имя = значение\nandroidWaveForTwo = on", Keys.EXP_OVERRIDES);
    }

    private void morePage(ScrollView[] h) {
        LinearLayout col = page("Ещё", null, h);
        LinearLayout g = section(col, "Вход из Яндекс Музыки");
        switchRow(g, "Кнопка Yume в настройках Музыки", "Плавающая кнопка на экране настроек", Keys.ENTRY_BUTTON, true);
        switchRow(g, "Ярлык на иконке Музыки", "Долгое нажатие на иконку → «Настройки Yume»", Keys.ENTRY_SHORTCUT, true);

        g = section(col, "Совместимость");
        List<String[]> lines = statusLines();
        if (lines.isEmpty()) {
            g.addView(Ui.row(this, "Откройте Yume из Яндекс Музыки",
                    "Тогда здесь появится отчёт: какие функции нашли своё место в установленной версии", null));
        } else {
            if (appVersion != null) g.addView(Ui.row(this, "Яндекс Музыка", appVersion, null));
            for (String[] l : lines) {
                boolean ok = !l[1].startsWith("не ");
                TextView mark = Ui.text(this, ok ? "✓" : "✕", 18, true, ok ? Ui.GOOD : Ui.BAD);
                g.addView(Ui.row(this, l[0], l[1], mark));
            }
        }
        g.addView(Ui.row(this, "Цвета, шрифты, масштаб, анимации",
                "Работают на уровне Android и не зависят от версии Музыки", Ui.text(this, "✓", 18, true, Ui.GOOD)));

        g = section(col, "Классические экраны");
        textArea(g, "Скрыть по id", "id ресурса, по строке", Keys.HIDE_IDS);
        textArea(g, "Скрыть по тексту", "часть текста, по строке", Keys.HIDE_TEXTS);
        switchRow(g, "Инспектор экранов", "Структура экранов пишется в лог LSPosed", Keys.INSPECTOR, false);

        g = section(col, "Применение");
        switchRow(g, "Применять внешний вид сразу", "Открытый экран Музыки пересоздаётся", Keys.LIVE_APPLY, true);
        LinearLayout reset = Ui.row(this, "Сбросить все настройки", null, null);
        ((TextView) ((LinearLayout) reset.getChildAt(0)).getChildAt(0)).setTextColor(Ui.BAD);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Вернуть всё как было?")
                .setPositiveButton("Сбросить", (d, w) -> {
                    store.clearAll();
                    recreate();
                })
                .setNegativeButton("Отмена", null)
                .show());
        g.addView(reset);
        caption(col, "Yume · шрифты под лицензией SIL Open Font License 1.1");
    }

    // ---------------------------------------------------------------- rows

    private LinearLayout section(LinearLayout col, String title) {
        col.addView(Ui.sectionTitle(this, title));
        LinearLayout g = Ui.group(this);
        col.addView(g, new LinearLayout.LayoutParams(-1, -2));
        return g;
    }

    private void caption(LinearLayout col, String text) {
        TextView t = Ui.text(this, text, 12, false, Ui.subColor(this));
        t.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), 0);
        col.addView(t);
    }

    private void switchRow(LinearLayout g, String title, String desc, String key, boolean def) {
        Switch sw = Ui.toggle(this, store.getBoolean(key, def));
        LinearLayout row = Ui.row(this, title, desc, sw);
        sw.setOnCheckedChangeListener((b, checked) -> {
            store.putBoolean(key, checked);
            refreshAll();
        });
        row.setOnClickListener(v -> sw.toggle());
        g.addView(row);
    }

    /** A switch stored as membership of {@code id} in a comma-separated set under {@code key}. */
    private void setSwitchRow(LinearLayout g, String title, String desc, String key, String id) {
        Switch sw = Ui.toggle(this, idSet(key).contains(id));
        LinearLayout row = Ui.row(this, title, desc, sw);
        sw.setOnCheckedChangeListener((b, checked) -> {
            Set<String> ids = idSet(key);
            if (checked) {
                ids.add(id);
            } else {
                ids.remove(id);
            }
            store.putString(key, String.join(",", ids));
        });
        row.setOnClickListener(v -> sw.toggle());
        g.addView(row);
    }

    private Set<String> idSet(String key) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : store.getString(key, "").split(",")) {
            if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    private void colorRow(LinearLayout g, String title, String key, int def) {
        int sz = Ui.dp(this, 32);
        LinearLayout end = new LinearLayout(this);
        end.setGravity(Gravity.CENTER_VERTICAL);
        TextView hex = Ui.text(this, ColorPicker.toHex(store.getInt(key, def)), 13, false, Ui.subColor(this));
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setPadding(0, 0, Ui.dp(this, 10), 0);
        end.addView(hex);
        View sw = Ui.swatch(this, store.getInt(key, def), sz);
        end.addView(sw, new LinearLayout.LayoutParams(sz, sz));
        LinearLayout row = Ui.row(this, title, null, end);
        Runnable sync = () -> {
            int c = store.getInt(key, def);
            hex.setText(ColorPicker.toHex(c));
            ((GradientDrawable) sw.getBackground()).setColor(c);
        };
        refreshers.add(sync);
        row.setOnClickListener(v -> ColorPicker.show(this, title, store.getInt(key, def), picked -> {
            store.putInt(key, picked);
            refreshAll();
        }));
        g.addView(row);
    }

    private void sliderRow(LinearLayout g, String title, String key, int def, int min, int max, String fmt) {
        TextView value = Ui.chip(this, "");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = Ui.row(this, title, null, value);
        row.setPadding(row.getPaddingLeft(), row.getPaddingTop(), row.getPaddingRight(), 0);
        row.setMinimumHeight(0);
        box.addView(row);
        SeekBar seek = Ui.seek(this, max - min, store.getInt(key, def) - min);
        seek.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 16));
        box.addView(seek);
        Runnable update = () -> value.setText(String.format(Locale.ROOT, fmt, seek.getProgress() + min));
        update.run();
        refreshers.add(() -> {
            seek.setProgress(store.getInt(key, def) - min);
            update.run();
        });
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
                refreshAll();
            }
        });
        // Tap the value to reset.
        value.setOnClickListener(v -> {
            store.putInt(key, def);
            refreshAll();
        });
        g.addView(box);
    }

    private void choiceRow(LinearLayout g, String title, String key, String[] ids, String[] names, String def) {
        TextView value = Ui.chip(this, "");
        LinearLayout row = Ui.row(this, title, null, value);
        Runnable sync = () -> value.setText(names[indexOf(ids, store.getString(key, def))]);
        sync.run();
        refreshers.add(sync);
        row.setOnClickListener(v -> Ui.sheet(this, title, names, indexOf(ids, store.getString(key, def)), null, which -> {
            store.putString(key, ids[which]);
            refreshAll();
        }));
        g.addView(row);
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    private void textArea(LinearLayout g, String title, String hint, String key) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 18);
        box.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 14));
        box.addView(Ui.text(this, title, 16, false, Ui.textColor(this)));
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
        int ip = Ui.dp(this, 12);
        e.setPadding(ip, ip, ip, ip);
        e.setBackground(Ui.rounded(Ui.card2Color(this), Ui.dp(this, 14)));
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, -2);
        el.topMargin = Ui.dp(this, 10);
        box.addView(e, el);
        TextView save = Ui.chip(this, "Сохранить");
        save.setOnClickListener(v -> {
            store.putString(key, e.getText().toString());
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.gravity = Gravity.END;
        sl.topMargin = Ui.dp(this, 10);
        box.addView(save, sl);
        g.addView(box);
    }

    /** One font slot: system families, bundled fonts and "own file", previewed in their typeface. */
    private void fontRow(LinearLayout g, String title, boolean bold) {
        String key = bold ? Keys.FONT_BOLD_SOURCE : Keys.FONT_SOURCE;
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (bold) {
            ids.add(Keys.FONT_SAME);
            names.add("Как основной");
        }
        for (int i = 0; i < Keys.FONT_IDS.length - 1; i++) {
            ids.add(Keys.FONT_IDS[i]);
            names.add(SYSTEM_FONT_NAMES[i]);
        }
        for (int i = 0; i < Keys.BUNDLED_FONT_IDS.length; i++) {
            ids.add(Keys.FONT_BUNDLED + Keys.BUNDLED_FONT_IDS[i]);
            names.add(Keys.BUNDLED_FONT_NAMES[i]);
        }
        ids.add(Keys.FONT_CUSTOM);
        names.add("Свой файл…");
        String def = bold ? Keys.FONT_SAME : Keys.FONT_OFF;

        TextView value = Ui.chip(this, "");
        LinearLayout row = Ui.row(this, title, null, value);
        Runnable sync = () -> {
            String cur = store.getString(key, def);
            int i = ids.indexOf(cur);
            value.setText(i >= 0 ? names.get(i) : names.get(0));
            value.setTypeface(preview(cur));
        };
        sync.run();
        refreshers.add(sync);
        row.setOnClickListener(v -> Ui.sheet(this, title, names.toArray(new String[0]),
                Math.max(0, ids.indexOf(store.getString(key, def))),
                (item, index) -> item.setTypeface(preview(ids.get(index))),
                which -> {
                    String id = ids.get(which);
                    if (Keys.FONT_CUSTOM.equals(id)) {
                        pickFont(bold);
                    } else if (id.startsWith(Keys.FONT_BUNDLED)) {
                        installBundledFont(bold, id);
                    } else {
                        store.putString(key, id);
                        refreshAll();
                    }
                }));
        g.addView(row);
    }

    private Typeface preview(String id) {
        try {
            if (id.startsWith(Keys.FONT_BUNDLED)) {
                // Variable fonts default to their first instance (often Thin); show Regular.
                Typeface t = new Typeface.Builder(getAssets(),
                        "fonts/" + id.substring(Keys.FONT_BUNDLED.length()) + ".ttf")
                        .setFontVariationSettings("'wght' 400")
                        .build();
                if (t != null) return t;
            }
            for (int i = 0; i < Keys.FONT_IDS.length; i++) {
                if (Keys.FONT_IDS[i].equals(id) && Keys.FONT_FAMILIES[i] != null) {
                    return Typeface.create(Keys.FONT_FAMILIES[i], Typeface.NORMAL);
                }
            }
        } catch (Throwable ignored) {
            // fall through to the default
        }
        return Typeface.DEFAULT;
    }

    private void installBundledFont(boolean bold, String id) {
        new Thread(() -> {
            String msg = null;
            try (InputStream in = getAssets().open("fonts/" + id.substring(Keys.FONT_BUNDLED.length()) + ".ttf")) {
                store.writeFont(bold, in);
                store.putString(bold ? Keys.FONT_BOLD_SOURCE : Keys.FONT_SOURCE, id);
            } catch (Throwable t) {
                msg = "Не удалось установить шрифт: " + t.getMessage();
            }
            String m = msg;
            runOnUiThread(() -> {
                if (m != null) Toast.makeText(this, m, Toast.LENGTH_LONG).show();
                refreshAll();
            });
        }).start();
    }

    // ---------------------------------------------------------------- actions

    private void pickFont(boolean bold) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf",
                "application/x-font-otf", "application/font-sfnt", "application/octet-stream"});
        try {
            startActivityForResult(i, bold ? REQ_FONT_BOLD : REQ_FONT);
        } catch (Exception e) {
            Toast.makeText(this, "Нет приложения для выбора файлов", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode != REQ_FONT && requestCode != REQ_FONT_BOLD) || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        boolean bold = requestCode == REQ_FONT_BOLD;
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
                    store.writeFont(bold, in);
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                store.putString(bold ? Keys.FONT_BOLD_SOURCE : Keys.FONT_SOURCE, Keys.FONT_CUSTOM);
                msg = "Шрифт установлен";
            } catch (Throwable t) {
                msg = "Не удалось установить шрифт: " + t.getMessage();
            }
            String m = msg;
            runOnUiThread(() -> {
                Toast.makeText(this, m, Toast.LENGTH_LONG).show();
                refreshAll();
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
