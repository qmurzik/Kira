package ru.adblock.lsposed;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/** Небольшой кэширующий помощник для рефлексивных вызовов публичных методов. */
final class Reflect {
    private static final ConcurrentHashMap<String, Method> CACHE = new ConcurrentHashMap<>();

    private Reflect() {
    }

    /** Вызов публичного метода по имени и числу аргументов. Исключения цели пробрасываются как есть. */
    static Object call(Object target, String name, Object... args) throws Throwable {
        Class<?> c = target.getClass();
        String key = c.getName() + '#' + name + '/' + args.length;
        Method m = CACHE.get(key);
        if (m == null) {
            m = find(c, name, args);
            m.setAccessible(true);
            CACHE.put(key, m);
        }
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private static Method find(Class<?> c, String name, Object[] args) throws NoSuchMethodException {
        outer:
        for (Method m : c.getMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length != args.length) continue;
            for (int i = 0; i < p.length; i++) {
                if (args[i] != null && !p[i].isPrimitive() && !p[i].isInstance(args[i])) continue outer;
            }
            return m;
        }
        throw new NoSuchMethodException(c.getName() + "." + name);
    }
}
