package ru.adblock.lsposed;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Простые настройки: переключатели слоёв и дополнительные домены/ключи. */
public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private boolean moduleActive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = openPrefs();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView status = text(moduleActive
                ? "Модуль активен. После изменения настроек принудительно остановите целевые приложения."
                : "Модуль не активен в LSPosed: настройки не будут доступны приложениям. "
                + "Включите модуль в LSPosed Manager и перезапустите это окно.", 14);
        status.setTextColor(moduleActive ? 0xFF2E7D32 : 0xFFC62828);
        root.addView(status);

        root.addView(header("Слои"));
        root.addView(toggle(Prefs.LAYER_SDK, "1. Рекламные SDK",
                "Yandex Mobile Ads, myTarget / VK Реклама, Google Ads: загрузка завершается «нет рекламы»"));
        root.addView(toggle(Prefs.LAYER_DNS, "2. Блокировка рекламных доменов",
                "InetAddress и okhttp3.Dns → UnknownHostException"));
        root.addView(toggle(Prefs.LAYER_JSON, "3. Фильтрация JSON",
                "Удаление элементов массивов с erid / isAd / promoted … (org.json и OkHttp)"));
        root.addView(toggle(Prefs.LAYER_UI, "4. UI-эвристика",
                "Схлопывание ячеек RecyclerView с меткой «Реклама», «erid», «Промо»"));
        root.addView(toggle(Prefs.DEBUG_LOG, "Подробный лог",
                "Писать каждое срабатывание в журнал LSPosed (тег RuAdBlock)"));

        root.addView(header("Дополнительные домены"));
        root.addView(text("По одному на строку. example.com — домен и поддомены, ~слово — подстрока.", 13));
        EditText domains = edit(prefs.getString(Prefs.EXTRA_DOMAINS, ""));
        root.addView(domains);

        root.addView(header("Дополнительные JSON-ключи"));
        root.addView(text("По одному на строку: flag:isBanner (== true), marker:adToken (есть и не пуст), "
                + "keep:offerId (никогда не удалять).", 13));
        EditText keys = edit(prefs.getString(Prefs.EXTRA_JSON_KEYS, ""));
        root.addView(keys);

        Button save = new Button(this);
        save.setText("Сохранить");
        save.setOnClickListener(v -> {
            prefs.edit()
                    .putString(Prefs.EXTRA_DOMAINS, domains.getText().toString().trim())
                    .putString(Prefs.EXTRA_JSON_KEYS, keys.getText().toString().trim())
                    .apply();
            Toast.makeText(this, "Сохранено. Перезапустите целевые приложения.", Toast.LENGTH_SHORT).show();
        });
        root.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("Версия " + BuildConfig.VERSION_NAME, 12));

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.addView(root);
        setContentView(scroll);
    }

    /**
     * LSPosed (xposedsharedprefs) разрешает MODE_WORLD_READABLE для активного модуля и
     * отдаёт файл XSharedPreferences в процессах приложений. Без LSPosed будет SecurityException.
     */
    @SuppressLint("WorldReadableFiles")
    @SuppressWarnings("deprecation")
    private SharedPreferences openPrefs() {
        try {
            SharedPreferences p = getSharedPreferences(Prefs.FILE, Context.MODE_WORLD_READABLE);
            moduleActive = true;
            return p;
        } catch (SecurityException e) {
            moduleActive = false;
            return getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE);
        }
    }

    private LinearLayout toggle(String key, String title, String summary) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 16);
        TextView s = text(summary, 13);
        s.setAlpha(0.7f);
        texts.addView(t);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, !Prefs.DEBUG_LOG.equals(key)));
        sw.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(key, checked).apply());
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }

    private TextView header(String s) {
        TextView t = text(s, 18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(20), 0, dp(4));
        return t;
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    private EditText edit(String value) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setMinLines(3);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setTypeface(Typeface.MONOSPACE);
        return e;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
