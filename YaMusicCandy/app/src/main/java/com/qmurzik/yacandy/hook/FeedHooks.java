package com.qmurzik.yacandy.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filters the blocks of "skeleton" screens (main screen, artist pages, concerts...). Every block
 * the backend sends goes through SkeletonJsonAdapter, which maps its "type" to a DTO class; the
 * adapter already returns null for unknown types and callers skip nulls, so dropping a block is
 * just returning null. DTO class names are kept by the app (Gson), so they are stable anchors.
 */
final class FeedHooks {
    private FeedHooks() {
    }

    private static final String ADAPTER =
            "com.yandex.music.shared.skeleton.data.core.api.skeleton.SkeletonJsonAdapter";
    private static final Map<Class<?>, Method> typeGetters = new ConcurrentHashMap<>();
    private static final Set<String> logged = ConcurrentHashMap.newKeySet();

    static void install(CandyModule m, ClassLoader cl) {
        List<Class<?>> adapters = new ArrayList<>();
        try {
            adapters.add(Class.forName(ADAPTER, false, cl));
        } catch (Throwable t) {
            // Renamed in this build: take every Gson deserializer of the skeleton packages.
            for (DexScanner.MethodRef r : m.finder().users(Finder.JSON_DESERIALIZERS)) {
                if (!r.owner.startsWith("Lcom/yandex/music/shared/skeleton/")) continue;
                try {
                    adapters.add(m.finder().classOf(r));
                } catch (Throwable ignored) {
                    // skip
                }
            }
        }
        int hooked = 0;
        for (Class<?> adapter : adapters) {
            for (Method method : adapter.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (p.length != 3 || !"com.google.gson.JsonElement".equals(p[0].getName())) continue;
                m.hookSafely(method, chain -> {
                    Object block = chain.proceed();
                    Config c = m.config();
                    if (block == null || c == null || !c.feedOn()) return block;
                    try {
                        return filter(m, c, block);
                    } catch (Throwable t) {
                        m.logE("feed filter", t);
                        return block;
                    }
                });
                hooked++;
            }
        }
        m.logI("feed hooks: " + hooked);
        m.status("Блоки главной", hooked > 0 ? "найдено адаптеров: " + adapters.size() : "не найдено");
    }

    private static Object filter(CandyModule m, Config c, Object block) throws ReflectiveOperationException {
        String dto = block.getClass().getSimpleName();
        String type = type(block);
        if (c.feedLog && logged.add(dto + "/" + type)) {
            m.logI("block " + dto + " type=" + type);
        }
        if (c.hiddenDtos.contains(dto)) return null;
        if (type != null && c.hiddenTypes.contains(type.toLowerCase(Locale.ROOT))) return null;
        if ("TabsBlockDto".equals(dto)) filterTabs(m, c, block);
        return block;
    }

    private static String type(Object block) {
        Method g = typeGetters.computeIfAbsent(block.getClass(), k -> {
            try {
                Method method = k.getMethod("getType");
                return method.getReturnType() == String.class ? method : null;
            } catch (NoSuchMethodException e) {
                return null;
            }
        });
        if (g == null) return null;
        try {
            return (String) g.invoke(block);
        } catch (Throwable t) {
            return null;
        }
    }

    /** TabsBlockDto.data.tabs is a list of TabDataDto with a title; drops the configured ones. */
    private static void filterTabs(CandyModule m, Config c, Object block) throws ReflectiveOperationException {
        Object data = field(block, "data");
        if (data == null) return;
        Field tabsField = data.getClass().getDeclaredField("tabs");
        tabsField.setAccessible(true);
        Object tabs = tabsField.get(data);
        if (!(tabs instanceof List)) return;
        List<Object> kept = new ArrayList<>();
        for (Object tab : (List<?>) tabs) {
            Object t = tab != null ? field(tab, "title") : null;
            String title = t instanceof String ? ((String) t).toLowerCase(Locale.ROOT) : "";
            if (c.feedLog && logged.add("tab/" + title)) m.logI("tab \"" + title + "\"");
            if (!matches(c.hiddenTabs, title)) kept.add(tab);
        }
        List<?> all = (List<?>) tabs;
        // Keep at least one tab, the screen expects a selected one.
        if (kept.isEmpty() || kept.size() == all.size()) return;
        tabsField.set(data, kept);
        try {
            Field sel = data.getClass().getDeclaredField("selectedTabIndex");
            sel.setAccessible(true);
            Object idx = sel.get(data);
            if (idx instanceof Integer) {
                int i = (Integer) idx;
                int now = i >= 0 && i < all.size() ? kept.indexOf(all.get(i)) : -1;
                sel.set(data, Math.max(now, 0));
            }
        } catch (NoSuchFieldException ignored) {
            // older/newer DTO without an explicit selection
        }
    }

    private static boolean matches(Set<String> needles, String title) {
        for (String n : needles) if (!title.isEmpty() && title.contains(n)) return true;
        return false;
    }

    private static Object field(Object o, String name) throws IllegalAccessException {
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
                // look in the superclass
            }
        }
        return null;
    }
}
