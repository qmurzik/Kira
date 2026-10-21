package ru.adblock.lsposed;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Слой 4: UI-эвристика для классических View (Compose не покрывается — там работает слой 3).
 * <p>
 * TextView с короткой меткой рекламы («Реклама», «erid: …», «Промо»…) запоминаются.
 * Перед каждой отрисовкой окна состояние пересчитывается заново:
 * <ul>
 * <li>если метка видима и находится внутри элемента списка — элемент схлопывается до
 * нулевого размера через LayoutParams (не GONE);</li>
 * <li>если ячейку переиспользовали под обычный контент (метка скрыта или текст сменился) —
 * исходные LayoutParams восстанавливаются.</li>
 * </ul>
 * Так обычные товары не пропадают, даже если RecyclerView/Adapter обфусцированы.
 * Элемент списка определяется по структуре: LayoutParams ячейки RecyclerView ссылаются на
 * ViewHolder, у которого есть поле с самой ячейкой (itemView); также поддерживается AbsListView.
 */
final class UiHooks {
    private static final int MAX_DEPTH = 20;

    /** TextView, в которых сейчас текст-метка рекламы. */
    private static final Set<TextView> LABELS = Collections.newSetFromMap(new WeakHashMap<>());
    /** Схлопнутые элементы и их исходные LayoutParams. */
    private static final Map<View, SavedLp> COLLAPSED = new WeakHashMap<>();
    private static final Map<ViewTreeObserver, Boolean> OBSERVERS = new WeakHashMap<>();

    /** Для класса LayoutParams: поле со ссылкой на ViewHolder (или NO_FIELD). */
    private static final Map<Class<?>, Field> LP_HOLDER = new ConcurrentHashMap<>();
    /** Для класса ViewHolder: поле itemView (или NO_FIELD). */
    private static final Map<Class<?>, Field> HOLDER_ITEM = new ConcurrentHashMap<>();
    /** Неудачные попытки найти ViewHolder по классу LayoutParams — после нескольких сдаёмся. */
    private static final Map<Class<?>, Integer> LP_FAILS = new ConcurrentHashMap<>();
    private static final int MAX_LP_FAILS = 10;
    private static final Field NO_FIELD;

    static {
        Field f = null;
        try {
            f = UiHooks.class.getDeclaredField("MAX_DEPTH");
        } catch (Throwable ignored) {
        }
        NO_FIELD = f;
    }

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
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class,
                    TextView.BufferType.class, boolean.class, int.class, SET_TEXT);
            L.i("UI-слой: хук TextView.setText установлен");
        } catch (Throwable t) {
            L.e("не удалось хукнуть TextView.setText", t);
        }
    }

    private static final XC_MethodHook SET_TEXT = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                TextView tv = (TextView) param.thisObject;
                if (AdText.matches((CharSequence) param.args[0])) {
                    LABELS.add(tv);
                    // После прикрепления к окну подписываемся на его отрисовку.
                    tv.post(() -> observe(tv));
                } else if (!LABELS.isEmpty()) {
                    LABELS.remove(tv);
                }
            } catch (Throwable t) {
                L.e("ошибка UI-хука", t);
            }
        }
    };

    private static void observe(View v) {
        try {
            if (!v.isAttachedToWindow()) return;
            ViewTreeObserver vto = v.getViewTreeObserver();
            if (!vto.isAlive() || OBSERVERS.containsKey(vto)) return;
            OBSERVERS.put(vto, Boolean.TRUE);
            vto.addOnPreDrawListener(UiHooks::reconcile);
            reconcile();
        } catch (Throwable t) {
            L.e("ошибка подписки на отрисовку", t);
        }
    }

    /** Возвращает false (отменить кадр), если разметка изменилась — следующий кадр будет верным. */
    private static boolean reconcile() {
        if (LABELS.isEmpty() && COLLAPSED.isEmpty()) return true;
        boolean changed = false;
        try {
            Map<View, Boolean> want = new IdentityHashMap<>();
            for (TextView tv : new ArrayList<>(LABELS)) {
                if (tv == null || !tv.isAttachedToWindow()) continue;
                if (!AdText.matches(tv.getText())) continue;
                View item = findItemView(tv);
                if (item != null && visibleUpTo(tv, item)) want.put(item, Boolean.TRUE);
            }
            // Восстанавливаем прикреплённые ячейки, где рекламы больше нет.
            for (View item : new ArrayList<>(COLLAPSED.keySet())) {
                if (item == null || !item.isAttachedToWindow() || want.containsKey(item)) continue;
                restore(item);
                changed = true;
            }
            for (View item : want.keySet()) {
                if (!COLLAPSED.containsKey(item) && collapse(item)) changed = true;
            }
        } catch (Throwable t) {
            L.e("ошибка UI-слоя", t);
        }
        return !changed;
    }

    /** Ближайший предок метки, являющийся элементом списка. */
    private static View findItemView(View v) {
        View cur = v;
        for (int d = 0; d < MAX_DEPTH; d++) {
            ViewParent p = cur.getParent();
            if (!(p instanceof ViewGroup)) return null;
            ViewGroup list = (ViewGroup) p;
            if (list instanceof AbsListView || isRecyclerItem(cur)) {
                // Элемент размером со весь список — это страница (ViewPager2) или контейнер.
                if (list.getWidth() > 0 && list.getHeight() > 0 && !COLLAPSED.containsKey(cur)
                        && cur.getWidth() >= list.getWidth() && cur.getHeight() >= list.getHeight() * 9 / 10) {
                    return null;
                }
                return cur;
            }
            cur = list;
        }
        return null;
    }

    /** У ячейки RecyclerView LayoutParams → ViewHolder → itemView == сама ячейка. */
    private static boolean isRecyclerItem(View child) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp == null) return false;
        Class<?> lpc = lp.getClass();
        if (lpc.getName().startsWith("android.")) return false;
        Field holderField = LP_HOLDER.get(lpc);
        if (holderField == null) {
            holderField = findHolderField(lp, child);
            if (holderField == null) {
                Integer fails = LP_FAILS.get(lpc);
                int n = fails == null ? 1 : fails + 1;
                LP_FAILS.put(lpc, n);
                if (n >= MAX_LP_FAILS) LP_HOLDER.put(lpc, NO_FIELD);
                return false;
            }
            LP_HOLDER.put(lpc, holderField);
        }
        if (holderField == NO_FIELD) return false;
        try {
            Object holder = holderField.get(lp);
            if (holder == null) return false;
            Field item = itemField(holder.getClass(), holder, child);
            return item != null && item.get(holder) == child;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Field findHolderField(ViewGroup.LayoutParams lp, View child) {
        for (Class<?> k = lp.getClass(); k != null && !k.getName().startsWith("android."); k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                Class<?> t = f.getType();
                if (Modifier.isStatic(f.getModifiers()) || t.isPrimitive() || t == Rect.class
                        || t.isArray() || t.getName().startsWith("java.") || View.class.isAssignableFrom(t)) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object holder = f.get(lp);
                    if (holder != null && itemField(holder.getClass(), holder, child) != null) return f;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Field itemField(Class<?> holderClass, Object holder, View child) {
        Field cached = HOLDER_ITEM.get(holderClass);
        if (cached != null) return cached == NO_FIELD ? null : cached;
        Field found = null;
        outer:
        for (Class<?> k = holderClass; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !View.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    if (f.get(holder) == child) {
                        found = f;
                        break outer;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        // Отрицательный результат не кэшируем: у ViewHolder могут быть и другие View-поля.
        if (found != null) HOLDER_ITEM.put(holderClass, found);
        return found;
    }

    private static boolean visibleUpTo(View tv, View item) {
        if (tv.getAlpha() == 0f) return false;
        for (View v = tv; v != null; ) {
            if (v.getVisibility() != View.VISIBLE) return false;
            if (v == item) return true;
            ViewParent p = v.getParent();
            v = p instanceof View ? (View) p : null;
        }
        return false;
    }

    private static boolean collapse(View item) {
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        if (lp == null) return false;
        COLLAPSED.put(item, new SavedLp(lp));
        lp.width = 0;
        lp.height = 0;
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMargins(0, 0, 0, 0);
        }
        item.setLayoutParams(lp);
        Stats.hit("ui", "схлопнута рекламная ячейка " + item.getClass().getName());
        return true;
    }

    private static void restore(View item) {
        SavedLp saved = COLLAPSED.remove(item);
        ViewGroup.LayoutParams lp = item.getLayoutParams();
        if (saved == null || lp == null) return;
        saved.apply(lp);
        item.setLayoutParams(lp);
    }
}
