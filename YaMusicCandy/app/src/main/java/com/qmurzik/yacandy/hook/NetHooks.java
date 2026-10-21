package com.qmurzik.yacandy.hook;

import com.qmurzik.yacandy.Features;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network-level switches that don't depend on obfuscated code:
 * <ul>
 * <li>Retrofit creates every API as {@link Proxy#newProxyInstance}; for blocked interfaces (kept
 * names like PlayerInformersApi) each returned {@code retrofit2.Call} is replaced by one that fails
 * with an IOException. The app treats it as a failed load and simply shows nothing there.</li>
 * <li>Third-party tracker hosts fail DNS resolution.</li>
 * </ul>
 */
final class NetHooks {
    private NetHooks() {
    }

    private static final String RETROFIT_CALL = "retrofit2.Call";

    static void installApiBlocker(CandyModule m) {
        try {
            Method npi = Proxy.class.getDeclaredMethod("newProxyInstance",
                    ClassLoader.class, Class[].class, InvocationHandler.class);
            m.hookSafely(npi, chain -> {
                Config c = m.config();
                Class<?>[] ifaces = (Class<?>[]) chain.getArg(1);
                InvocationHandler h = (InvocationHandler) chain.getArg(2);
                if (c == null || ifaces == null || ifaces.length != 1 || h == null
                        || h instanceof ApiGate || h instanceof DeadCall || !isRetrofitService(ifaces[0])) {
                    return chain.proceed();
                }
                return chain.proceed(new Object[]{chain.getArg(0), ifaces, new ApiGate(m, ifaces[0].getName(), h)});
            });
            m.status("Промо-запросы", "перехват Retrofit");
        } catch (Throwable t) {
            m.logE("api blocker", t);
        }
    }

    /** A Retrofit service interface has methods returning retrofit2.Call. */
    private static boolean isRetrofitService(Class<?> iface) {
        try {
            for (Method method : iface.getDeclaredMethods()) {
                if (callInterface(method.getReturnType()) != null) return true;
            }
        } catch (Throwable ignored) {
            // unusual interface, leave it alone
        }
        return false;
    }

    private static final Map<Method, String> paths = new ConcurrentHashMap<>();

    /**
     * The endpoint path of a Retrofit method: the String value of its HTTP annotation. Annotation
     * classes are obfuscated, so any String-valued annotation member containing a path is taken.
     */
    static String path(Method method) {
        String cached = paths.get(method);
        if (cached != null) return cached;
        String found = "";
        try {
            for (Annotation a : method.getDeclaredAnnotations()) {
                for (Method member : a.annotationType().getDeclaredMethods()) {
                    if (member.getReturnType() != String.class || member.getParameterCount() != 0) continue;
                    member.setAccessible(true);
                    Object v = member.invoke(a);
                    if (v instanceof String && !((String) v).isEmpty() && !((String) v).contains(" ")
                            && (((String) v).contains("/") || ((String) v).matches("[a-z0-9-]+"))) {
                        String sv = (String) v;
                        found = sv.startsWith("/") ? sv.substring(1) : sv;
                        int q = found.indexOf('?');
                        if (q >= 0) found = found.substring(0, q);
                        break;
                    }
                }
                if (!found.isEmpty()) break;
            }
        } catch (Throwable ignored) {
            // no path, matched by interface name only
        }
        paths.put(method, found);
        return found;
    }

    /** Wraps a Retrofit service; the blocked state is checked per call so toggles apply live. */
    private static final class ApiGate implements InvocationHandler {
        private final CandyModule m;
        private final String api;
        private final InvocationHandler real;

        ApiGate(CandyModule m, String api, InvocationHandler real) {
            this.m = m;
            this.api = api;
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result;
            try {
                result = real.invoke(proxy, method, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
            Config c = m.config();
            if (result == null || c == null) return result;
            Class<?> callIface = callInterface(method.getReturnType());
            if (callIface == null) return result;
            if (!c.blockedApis.contains(api) && !c.pathBlocked(path(method))) return result;
            return Proxy.newProxyInstance(callIface.getClassLoader(), new Class<?>[]{callIface},
                    new DeadCall(callIface, result));
        }
    }

    private static Class<?> callInterface(Class<?> type) {
        return type != null && type.isInterface() && RETROFIT_CALL.equals(type.getName()) ? type : null;
    }

    /**
     * A retrofit2.Call that never hits the network. Method names are obfuscated, so they are told
     * apart by shape: enqueue takes a single callback, execute returns a Response.
     */
    private static final class DeadCall implements InvocationHandler {
        private final Class<?> callIface;
        private final Object original;
        private volatile boolean canceled;

        DeadCall(Class<?> callIface, Object original) {
            this.callIface = callIface;
            this.original = original;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            Class<?>[] p = method.getParameterTypes();
            Class<?> ret = method.getReturnType();
            if (method.getDeclaringClass() == Object.class) {
                switch (name) {
                    case "equals": return proxy == args[0];
                    case "hashCode": return System.identityHashCode(proxy);
                    default: return "YaCandy blocked call";
                }
            }
            if (p.length == 1 && ret == void.class && p[0].isInterface()) {
                failCallback(proxy, args[0]);
                return null;
            }
            if (name.equals("clone") || ret == callIface) {
                return Proxy.newProxyInstance(callIface.getClassLoader(), new Class<?>[]{callIface},
                        new DeadCall(callIface, original));
            }
            if (name.equals("cancel")) {
                canceled = true;
                return null;
            }
            if (p.length == 0 && "retrofit2.Response".equals(ret.getName())) {
                throw blocked();
            }
            if (ret == boolean.class) return canceled;
            // request(), timeout(): harmless, ask the real call.
            try {
                return method.invoke(original, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }

        /** Calls the callback method that takes (Call, Throwable), i.e. onFailure. */
        private void failCallback(Object call, Object callback) throws Throwable {
            if (callback == null) return;
            for (Method cb : callback.getClass().getMethods()) {
                Class<?>[] p = cb.getParameterTypes();
                if (p.length == 2 && p[0].isAssignableFrom(callIface) && p[1] == Throwable.class) {
                    cb.setAccessible(true);
                    try {
                        cb.invoke(callback, call, blocked());
                    } catch (InvocationTargetException e) {
                        throw e.getCause() != null ? e.getCause() : e;
                    }
                    return;
                }
            }
        }
    }

    private static IOException blocked() {
        return new IOException("Blocked by YaCandy");
    }

    static void installTrackerBlocker(CandyModule m) {
        try {
            m.hookPassthrough(InetAddress.class.getDeclaredMethod("getAllByName", String.class), chain -> {
                checkHost(m, (String) chain.getArg(0));
                return chain.proceed();
            });
            m.hookPassthrough(InetAddress.class.getDeclaredMethod("getByName", String.class), chain -> {
                checkHost(m, (String) chain.getArg(0));
                return chain.proceed();
            });
        } catch (Throwable t) {
            m.logE("tracker blocker", t);
        }
    }

    private static void checkHost(CandyModule m, String host) throws UnknownHostException {
        Config c = m.config();
        if (c == null || !c.telemetryOff || host == null) return;
        String h = host.toLowerCase(Locale.ROOT);
        for (String t : Features.TRACKER_HOSTS) {
            if (h.contains(t)) throw new UnknownHostException("Blocked by YaCandy: " + host);
        }
    }
}
