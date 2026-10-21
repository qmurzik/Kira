package ru.adblock.lsposed;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Слой 4: UI-эвристика для классических View (Compose не покрывается — там работает слой 3).
 * <p>
 * Если TextView получает короткий текст-метку рекламы («Реклама», «erid: …», «Промо»…),
 * ближайший родитель, являющийся элементом RecyclerView, схлопывается до нулевого размера
 * через LayoutParams (не GONE). Исходные LayoutParams восстанавливаются при каждом
 * переиспользовании ячейки (RecyclerView.Adapter.bindViewHolder), поэтому обычные
 * карточки не пропадают.
 */
final class UiHooks {
    private static final String SAVED = "ruadblock.savedLp";
    private static final String TRIGGER = "ruadblock.trigger";
    private static final int MAX_DEPTH = 20;

    private static final String[] ADAPTERS = {
            "androidx.recyclerview.widget.RecyclerView$Adapter",
            "android.support.v7.widget.RecyclerView$Adapter",
    };
    private static final String[] VIEW_HOLDERS = {
            "androidx.recyclerview.widget.RecyclerView$ViewHolder",
            "android.support.v7.widget.RecyclerView$ViewHolder",
    };

    private static final Map<Class<?>, Boolean> RV_CLASS_CACHE = new ConcurrentHashMap<>();
    private static volatile Field itemViewField;
    /** Были ли схлопывания в процессе — чтобы не трогать доп. поля на горячем пути setText. */
    private static volatile boolean anyCollapsed;

    private UiHooks() {
    }

    private static final class SavedLp {
        final int width, height, left, top, right, bottom;
        final boolean margins;

        SavedLp(ViewGroup.LayoutParams lp) {
            width = lp.width;
            height = lp.height;
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                margins = true;
                left = m.leftMargin;
                top = m.topMargin;
                right = m.rightMargin;
                bottom = m.bottomMargin;
            } else {
                margins = false;
                left = top = right = bottom = 0;
            }
        }

        void apply(ViewGroup.LayoutParams lp) {
            lp.width = width;
            lp.height = height;
            if (margins && lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).setMargins(left, top, right, bottom);
            }
        }
    }

    static void install(ClassLoader cl) {
        // Без восстановления при переиспользовании ячеек схлопывать нельзя — иначе пропадут
        // обычные товары. Поэтому сначала ставим хук восстановления.
        boolean restore = false;
        for (int i = 0; i < ADAPTERS.length; i++) {
            Class<?> adapter = XposedHelpers.findClassIfExists(ADAPTERS[i], cl);
            Class<?> holder = XposedHelpers.findClassIfExists(VIEW_HOLDERS[i], cl);
            if (adapter == null || holder == null) continue;
            try {
                Field f = holder.getField("itemView");
                f.setAccessible(true);
                itemViewField = f;
                if (!XposedBridge.hookAllMethods(adapter, "bindViewHolder", RESTORE).isEmpty()) {
                    restore = true;
                    break;
                }
            } catch (Throwable t) {
                L.e("не удалось хукнуть " + ADAPTERS[i] + ".bindViewHolder", t);
            }
        }
        if (!restore) {
            L.d("RecyclerView.Adapter.bindViewHolder не найден (обфускация?) — UI-слой отключён");
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class,
                    TextView.BufferType.class, boolean.class, int.class, SET_TEXT);
            L.d("UI-хуки установлены");
        } catch (Throwable t) {
            L.e("не удалось хукнуть TextView.setText", t);
        }
    }

    private static final XC_MethodHook RESTORE = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                Object holder = param.args.length > 0 ? param.args[0] : null;
                Field f = itemViewField;
                if (holder == null || f == null) return;
                Object v = f.get(holder);
                if (v instanceof View) restore((View) v);
            } catch (Throwable t) {
                L.e("ошибка восстановления ячейки", t);
            }
        }
    };

    private static final XC_MethodHook SET_TEXT = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                TextView tv = (TextView) param.thisObject;
                if (!AdText.matches((CharSequence) param.args[0])) {
                    if (!anyCollapsed) return;
                    // Метка в той же ячейке получила обычный текст — разворачиваем обратно.
                    Object prevItem = XposedHelpers.getAdditionalInstanceField(tv, TRIGGER);
                    if (prevItem instanceof View) {
                        XposedHelpers.removeAdditionalInstanceField(tv, TRIGGER);
                        restore((View) prevItem);
                    }
                    return;
                }
                // Текст ставится во время bind, когда ячейка ещё не прикреплена к RecyclerView
                // и видимость метки может поменяться позже. Проверяем после прикрепления/разметки.
                tv.post(() -> check(tv));
            } catch (Throwable t) {
                L.e("ошибка UI-хука", t);
            }
        }
    };

    private static void check(TextView tv) {
        try {
            if (!AdText.matches(tv.getText())) return;
            View item = findItemView(tv);
            if (item == null) return;
            if (!visibleUpTo(tv, item)) return;
            collapse(item, tv);
        } catch (Throwable t) {
            L.e("ошибка проверки метки рекламы", t);
        }
    }

    /** Ближайший предок, чей родитель — RecyclerView (то есть itemView). */
    private static View findItemView(View v) {
        View cur = v;
        for (int d = 0; d < MAX_DEPTH; d++) {
            ViewParent p = cur.getParent();
            if (!(p instanceof ViewGroup)) return null;
            if (isRecyclerView(p.getClass())) {
                ViewGroup rv = (ViewGroup) p;
                // ViewPager2 тоже RecyclerView внутри — страницы целиком не схлопываем.
                ViewParent outer = rv.getParent();
                if (outer != null && outer.getClass().getName().contains("ViewPager2")) return null;
                // Ячейка размером со весь список — скорее страница/контейнер, а не карточка.
                if (rv.getHeight() > 0 && cur.getHeight() >= rv.getHeight()
                        && rv.getWidth() > 0 && cur.getWidth() >= rv.getWidth()) {
                    return null;
                }
                return cur;
            }
            cur = (View) p;
        }
        return null;
    }

    private static boolean isRecyclerView(Class<?> c) {
        Boolean cached = RV_CLASS_CACHE.get(c);
        if (cached != null) return cached;
        boolean r = false;
        for (Class<?> k = c; k != null && k != View.class; k = k.getSuperclass()) {
            if (k.getName().endsWith(".RecyclerView")) {
                r = true;
                break;
            }
        }
        RV_CLASS_CACHE.put(c, r);
        return r;
    }

    private static boolean visibleUpTo(View tv, View item) {
        if (!tv.isAttachedToWindow() || tv.getAlpha() == 0f) return false;
        for (View v = tv; v != null; ) {
            if (v.getVisibility() != View.VISIBLE) return false;
            if (v == item) return true;
            ViewParent p = v.getParent();
            v = p instanceof View ? (View) p : null;
        }
        return false;
    }

    private static void collapse(View item, TextView trigger) {
        if (XposedHelpers.getAdditionalInstanceField(item, SAVED) != null) return;
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        if (lp == null) return;
        XposedHelpers.setAdditionalInstanceField(item, SAVED, new SavedLp(lp));
        XposedHelpers.setAdditionalInstanceField(trigger, TRIGGER, item);
        anyCollapsed = true;
        lp.width = 0;
        lp.height = 0;
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMargins(0, 0, 0, 0);
        }
        item.setLayoutParams(lp);
        L.d("UI: схлопнута рекламная ячейка " + item.getClass().getName());
    }

    private static void restore(View item) {
        if (!anyCollapsed) return;
        Object saved = XposedHelpers.removeAdditionalInstanceField(item, SAVED);
        if (!(saved instanceof SavedLp)) return;
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        if (lp == null) return;
        ((SavedLp) saved).apply(lp);
        item.setLayoutParams(lp);
    }
}
