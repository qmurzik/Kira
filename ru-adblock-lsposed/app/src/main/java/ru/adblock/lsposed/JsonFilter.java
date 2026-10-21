package ru.adblock.lsposed;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.Iterator;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Слой 3: удаление рекламных элементов из JSON-массивов.
 * <p>
 * Удаляются только элементы массивов (объекты), у которых есть маркер (erid) или флаг
 * (isAd/advert/promoted/...) == true — у самого элемента или у его прямых вложенных объектов.
 * Корневой объект никогда не изменяется.
 */
final class JsonFilter {
    /** Максимальный размер обрабатываемого JSON (символов/байт). */
    static final int MAX_SIZE = 1536 * 1024;
    /** Ограничение на число посещённых узлов, чтобы обход был дешёвым. */
    private static final int MAX_NODES = 100_000;

    private static volatile Rules rules;

    private JsonFilter() {
    }

    static void init(Rules r) {
        rules = r;
    }

    /** Хуки на конструкторы org.json.JSONObject(String) / JSONArray(String). */
    static void installOrgJson() {
        XC_MethodHook after = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object arg = param.args[0];
                    if (!(arg instanceof String)) return;
                    String s = (String) arg;
                    if (!mightContainAds(s)) return;
                    int removed = filterTree(param.thisObject);
                    if (removed > 0) L.d("org.json: удалено рекламных элементов: " + removed);
                } catch (Throwable t) {
                    L.e("ошибка фильтра org.json", t);
                }
            }
        };
        try {
            XposedHelpers.findAndHookConstructor(JSONObject.class, String.class, after);
            XposedHelpers.findAndHookConstructor(JSONArray.class, String.class, after);
            L.d("org.json хуки установлены");
        } catch (Throwable t) {
            L.e("не удалось установить хуки org.json", t);
        }
    }

    /** Дешёвая предпроверка: строка не слишком большая и содержит хотя бы один ключ. */
    static boolean mightContainAds(String s) {
        Rules r = rules;
        if (r == null || s == null) return false;
        int n = s.length();
        if (n < 8 || n > MAX_SIZE) return false;
        for (String needle : r.jsonNeedles) {
            if (s.indexOf(needle) >= 0) return true;
        }
        return false;
    }

    /**
     * Парсит JSON, фильтрует и возвращает новую строку; null — если ничего не удалено
     * или это не JSON.
     */
    static String filterString(String s) {
        if (!mightContainAds(s)) return null;
        try {
            Object root = new JSONTokener(s).nextValue();
            if (!(root instanceof JSONObject) && !(root instanceof JSONArray)) return null;
            int removed = filterTree(root);
            if (removed == 0) return null;
            L.d("okhttp: удалено рекламных элементов: " + removed);
            return root.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Возвращает число удалённых элементов. */
    static int filterTree(Object root) {
        Rules r = rules;
        if (r == null) return 0;
        int[] budget = {MAX_NODES};
        return walk(root, r, budget);
    }

    private static int walk(Object node, Rules r, int[] budget) {
        if (--budget[0] < 0) return 0;
        int removed = 0;
        if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = arr.length() - 1; i >= 0; i--) {
                Object el = arr.opt(i);
                if (el instanceof JSONObject && isAdElement((JSONObject) el, r)) {
                    arr.remove(i);
                    removed++;
                } else if (el instanceof JSONObject || el instanceof JSONArray) {
                    removed += walk(el, r, budget);
                }
                if (budget[0] < 0) break;
            }
        } else if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                Object v = obj.opt(it.next());
                if (v instanceof JSONObject || v instanceof JSONArray) {
                    removed += walk(v, r, budget);
                    if (budget[0] < 0) break;
                }
            }
        }
        return removed;
    }

    static boolean isAdElement(JSONObject el, Rules r) {
        if (hasKeep(el, r)) return false;
        if (hasAdKey(el, r)) return true;
        // Прямые вложенные объекты: {"type":"banner","payload":{"erid":"..."}}
        Iterator<String> it = el.keys();
        while (it.hasNext()) {
            Object v = el.opt(it.next());
            if (v instanceof JSONObject) {
                JSONObject child = (JSONObject) v;
                if (hasKeep(child, r)) return false;
                if (hasAdKey(child, r)) return true;
            }
        }
        return false;
    }

    private static boolean hasKeep(JSONObject o, Rules r) {
        if (r.jsonKeep.isEmpty()) return false;
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            if (r.jsonKeep.contains(it.next().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean hasAdKey(JSONObject o, Rules r) {
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String key = it.next();
            String k = key.toLowerCase(Locale.ROOT);
            if (r.jsonFlags.contains(k)) {
                Object v = o.opt(key);
                if (Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v))) return true;
            }
            if (isMarker(key, k, r)) {
                Object v = o.opt(key);
                if (v != null && v != JSONObject.NULL && !String.valueOf(v).trim().isEmpty()
                        && !Boolean.FALSE.equals(v)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isMarker(String key, String lowerKey, Rules r) {
        if (r.jsonMarkers.contains(lowerKey)) return true;
        for (String m : r.jsonMarkers) {
            if (lowerKey.length() <= m.length() || !lowerKey.endsWith(m)) continue;
            // ad_erid, ad-erid
            char sep = lowerKey.charAt(lowerKey.length() - m.length() - 1);
            if (sep == '_' || sep == '-') return true;
            // advertErid: маркер начинается с заглавной буквы в camelCase
            if (Character.isUpperCase(key.charAt(key.length() - m.length()))
                    && Character.isLowerCase(key.charAt(key.length() - m.length() - 1))) {
                return true;
            }
        }
        return false;
    }
}
