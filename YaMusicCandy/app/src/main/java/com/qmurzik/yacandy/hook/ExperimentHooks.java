package com.qmurzik.yacandy.hook;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forces values of Yandex Music's server-side experiments (feature flags).
 * <ul>
 * <li>Every experiment value goes through one static resolver {@code (Experiments, String name)
 * -> String}, found by its "Unknown experiment - " message.</li>
 * <li>The experiment base class (found by its "_NotReady" name suffix) also exposes a JSON
 * "details" object; some features (e.g. the splash) read flags from there, so it is emptied for
 * experiments the module switches off.</li>
 * </ul>
 */
final class ExperimentHooks {
    private ExperimentHooks() {
    }

    private static final String NOT_READY = "_NotReady";
    private static final Map<String, String> seen = new ConcurrentHashMap<>();
    private static volatile boolean dumpScheduled;

    static void install(CandyModule m, Finder f) {
        m.status("Эксперименты", "резолвер не найден");
        for (DexScanner.MethodRef ref : f.users(Finder.EXP_RESOLVER)) {
            if (!ref.isStatic() || ref.params.length != 2 || !"Ljava/lang/String;".equals(ref.params[1])
                    || !"Ljava/lang/String;".equals(ref.ret)) continue;
            try {
                Executable resolver = f.resolve(ref);
                m.hookSafely(resolver, chain -> {
                    Object result = chain.proceed();
                    Config c = m.config();
                    if (c == null) return result;
                    String name = base((String) chain.getArg(1));
                    if (c.expDump) record(m, name, (String) result);
                    String forced = c.expOverrides.get(name);
                    return forced != null ? forced : result;
                });
                m.logI("experiments resolver: " + ref);
                m.status("Эксперименты", "резолвер найден");
            } catch (Throwable t) {
                m.logE("experiments resolver", t);
            }
        }
        for (DexScanner.MethodRef ref : f.users(Finder.EXP_BASE)) {
            if (!ref.isConstructor()) continue;
            try {
                hookDetails(m, f.classOf(ref));
            } catch (Throwable t) {
                m.logE("experiment details", t);
            }
        }
    }

    /** Hooks the no-arg "details" getter of the experiment base class. */
    private static void hookDetails(CandyModule m, Class<?> base) throws ReflectiveOperationException {
        for (Method method : base.getDeclaredMethods()) {
            Class<?> r = method.getReturnType();
            if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())
                    || r.isPrimitive() || r == String.class || r == Object.class) continue;
            Constructor<?> fromJson = jsonConstructor(r);
            if (fromJson == null) continue;
            Class<?> jsonObject = fromJson.getParameterTypes()[0];
            m.hookSafely(method, chain -> {
                Config c = m.config();
                if (c == null || c.expOverrides.isEmpty()) return chain.proceed();
                String forced = forcedFor(c, chain.getThisObject());
                if (forced == null || !isOff(forced)) return chain.proceed();
                return fromJson.newInstance(jsonObject.getDeclaredConstructor().newInstance());
            });
            m.logI("experiment details: " + method);
        }
    }

    /** The wrapper type of the details getter has a constructor taking a Gson JsonObject. */
    private static Constructor<?> jsonConstructor(Class<?> cls) {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 1 && "com.google.gson.JsonObject".equals(p[0].getName())) {
                c.setAccessible(true);
                return c;
            }
        }
        return null;
    }

    /** Finds the experiment's name among its String fields and returns its forced value, if any. */
    private static String forcedFor(Config c, Object experiment) throws IllegalAccessException {
        for (Class<?> k = experiment.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field fld : k.getDeclaredFields()) {
                if (fld.getType() != String.class || Modifier.isStatic(fld.getModifiers())) continue;
                fld.setAccessible(true);
                Object v = fld.get(experiment);
                if (v == null) continue;
                String forced = c.expOverrides.get(base((String) v));
                if (forced != null) return forced;
            }
        }
        return null;
    }

    private static boolean isOff(String v) {
        return "default".equals(v) || "off".equals(v) || "control".equals(v);
    }

    private static String base(String name) {
        return name != null && name.endsWith(NOT_READY)
                ? name.substring(0, name.length() - NOT_READY.length()) : name;
    }

    private static void record(CandyModule m, String name, String value) {
        if (name == null) return;
        seen.put(name, value == null ? "null" : value);
        if (dumpScheduled) return;
        dumpScheduled = true;
        // Let the app settle so most experiments have been read at least once.
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(20_000);
            } catch (InterruptedException ignored) {
                return;
            }
            StringBuilder sb = new StringBuilder("Experiments seen (server values):\n");
            for (Map.Entry<String, String> e : new TreeMap<>(seen).entrySet()) {
                sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
            }
            for (int i = 0; i < sb.length(); i += 3000) {
                m.logI(sb.substring(i, Math.min(sb.length(), i + 3000)));
            }
        }, "YaCandy-exp-dump");
        t.setDaemon(true);
        t.start();
    }
}
